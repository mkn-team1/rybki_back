package com.rybki.spring_boot.websocket;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rybki.spring_boot.service.AudioDumpService;
import com.rybki.spring_boot.service.BotService;
import com.rybki.spring_boot.service.IdeaService;
import com.rybki.spring_boot.service.SessionService;
import com.rybki.spring_boot.service.SttRoutingService;
import com.rybki.spring_boot.service.VoteService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientWebSocketHandler implements WebSocketHandler {

    private static final String CLIENT_ID_PARAM = "clientId";
    private static final String EVENT_ID_PARAM = "eventId";

    private final SessionService sessionService;
    private final SttRoutingService sttRoutingService;
    private final VoteService voteService;

    private final IdeaService ideaService;
    private final BotService botService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AudioDumpService audioDumpService;

    @Override
    @SuppressWarnings("null")
    public @NonNull Mono<Void> handle(@NonNull final WebSocketSession session) {
        log.info("Client connected: sessionId={}", session.getId());

        return registerFromQuery(session)
                .thenMany(session.receive())
                .flatMap(message -> switch (message.getType()) {
                    case TEXT -> handleTextMessage(session, message);
                    case BINARY -> handleBinaryMessage(session, message);
                    default -> Mono.empty();
                })
                .doFinally(signal -> handleDisconnect(session))
                .then();
    }

    private Mono<Void> handleTextMessage(final WebSocketSession session, final WebSocketMessage message) {
        return Mono.fromCallable(() -> objectMapper.readTree(message.getPayloadAsText()))
                .flatMap(jsonNode -> {
                    final String type = jsonNode.path("type").asText();
                    return switch (type) {
                        case "start" -> handleStart(session, jsonNode);
                        case "end" -> handleEnd(session);
                        case "vote" -> handleVote(session, jsonNode);
                        case "create_idea" -> handleCreateIdea(session, jsonNode);
                        case "delete_idea" -> handleDeleteIdea(session, jsonNode);
                        case "react_to_idea" -> handleReactToIdea(session, jsonNode);
                        case "connect_bot" -> handleConnectBot(session, jsonNode);
                        case "disconnect_bot" -> handleDisconnectBot(session, jsonNode);
                        default -> {
                            log.warn("Unknown message type: {}", type);
                            yield Mono.empty();
                        }
                    };
                })
                .onErrorResume(e -> {
                    log.error("Failed to handle text message", e);
                    return Mono.empty();
                });
    }

    @SuppressWarnings("null")
    private Mono<Void> handleStart(final WebSocketSession session, final JsonNode jsonNode) {
        if (!jsonNode.has(CLIENT_ID_PARAM) || !jsonNode.has(EVENT_ID_PARAM)) {
            log.warn("Missing clientId/eventId in start message: sessionId={}", session.getId());
            return session.close(CloseStatus.BAD_DATA);
        }

        final String conferenceId = jsonNode.get(CLIENT_ID_PARAM).asText();
        final String eventId = jsonNode.get(EVENT_ID_PARAM).asText();

        return audioDumpService.start(session.getId(), conferenceId, eventId)
                .then(sessionService.register(session, conferenceId, eventId))
                .doOnSuccess(v -> log.info("Start: conferenceId={}, eventId={}", conferenceId, eventId))
                .then();
    }

    private Mono<Void> handleEnd(final WebSocketSession session) {
        return audioDumpService.stop(session.getId())
                .then(sessionService.getSessionData(session)
                        .flatMap(cs -> sttRoutingService.notifyEnd(cs.conferenceId(), cs.eventId())
                                .then(sessionService.unregister(session))
                                .doOnSuccess(v -> log.info("End: sessionId={}", session.getId()))));
    }

    private Mono<Void> handleVote(final WebSocketSession session, final JsonNode jsonNode) {
        return sessionService.getSessionData(session)
                .flatMap(cs -> voteService.registerVote(cs.conferenceId(), cs.eventId(), jsonNode)
                        .doOnSuccess(v -> log.info("Vote: conferenceId={}, eventId={}", cs.conferenceId(),
                                cs.eventId()))
                        .then())
                .onErrorResume(e -> {
                    log.warn("Vote from unregistered session or error: sessionId={}", session.getId());
                    return Mono.empty();
                });
    }

    private Mono<Void> handleBinaryMessage(final WebSocketSession session, final WebSocketMessage message) {
        return sessionService.getSessionData(session)
                .flatMap(cs -> {
                    final byte[] bytes = new byte[message.getPayload().readableByteCount()];
                    message.getPayload().read(bytes);
                    return audioDumpService.append(session.getId(), bytes)
                            .then(sttRoutingService.forwardAudio(cs.conferenceId(), cs.eventId(), bytes))
                            .doOnError(
                                    e -> log.error("Failed to forward audio: conferenceId={}, eventId={}",
                                            cs.conferenceId(), cs.eventId(), e));
                })
                .onErrorResume(e -> {
                    log.warn("Binary from unregistered session or error: sessionId={}", session.getId());
                    return Mono.empty();
                });
    }

    private Mono<Void> handleCreateIdea(final WebSocketSession session, final JsonNode jsonNode) {
        return sessionService.getSessionData(session)
                .flatMap(cs -> {
                    final String ideaContent = jsonNode.path("content").asText();
                    if (!StringUtils.hasText(ideaContent)) {
                        log.warn("Empty idea content: conferenceId={}, eventId={}", cs.conferenceId(), cs.eventId());
                        return Mono.<Void>empty();
                    }
                    log.info("Create idea: conferenceId={}, eventId={}, content={}", cs.conferenceId(), cs.eventId(),
                            ideaContent);
                    return ideaService.createIdeaFromFront(cs.conferenceId(), cs.conferenceName(), cs.eventId(),
                            ideaContent, "");
                })
                .onErrorResume(e -> {
                    log.warn("Create idea from unregistered session or error: sessionId={}", session.getId());
                    return Mono.empty();
                });
    }

    private Mono<Void> handleDeleteIdea(final WebSocketSession session, final JsonNode jsonNode) {
        return sessionService.getSessionData(session)
                .flatMap(cs -> {
                    final String ideaId = jsonNode.path("ideaId").asText();
                    if (!StringUtils.hasText(ideaId)) {
                        log.warn("Empty ideaId: conferenceId={}, eventId={}", cs.conferenceId(), cs.eventId());
                        return Mono.<Void>empty();
                    }
                    log.info("Delete idea: conferenceId={}, eventId={}, ideaId={}", cs.conferenceId(), cs.eventId(),
                            ideaId);
                    return ideaService.deleteIdea(cs.conferenceId(), cs.eventId(), ideaId);
                })
                .onErrorResume(e -> {
                    log.warn("Delete idea from unregistered session or error: sessionId={}", session.getId());
                    return Mono.empty();
                });
    }

    private Mono<Void> handleReactToIdea(final WebSocketSession session, final JsonNode jsonNode) {
        return sessionService.getSessionData(session)
                .flatMap(cs -> {
                    final String ideaId = jsonNode.path("ideaId").asText();
                    final String reactionType = jsonNode.path("reaction").asText(); // "like", "dislike", or null
                    if (!StringUtils.hasText(ideaId)) {
                        log.warn("Empty ideaId: conferenceId={}, eventId={}", cs.conferenceId(), cs.eventId());
                        return Mono.<Void>empty();
                    }
                    log.info("React to idea: conferenceId={}, eventId={}, ideaId={}, reaction={}", cs.conferenceId(),
                            cs.eventId(), ideaId, reactionType);
                    return ideaService.reactToIdea(cs.conferenceId(), cs.eventId(), ideaId, reactionType);
                })
                .onErrorResume(e -> {
                    log.warn("React to idea from unregistered session or error: sessionId={}", session.getId());
                    return Mono.empty();
                });
    }

    private Mono<Void> handleConnectBot(final WebSocketSession session, final JsonNode jsonNode) {
        return sessionService.getSessionData(session)
                .flatMap(cs -> {
                    final String botId = jsonNode.path("botId").asText();
                    if (!StringUtils.hasText(botId)) {
                        log.warn("Empty botId: conferenceId={}, eventId={}", cs.conferenceId(), cs.eventId());
                        return Mono.<Void>empty();
                    }
                    log.info("Connect bot: conferenceId={}, eventId={}, botId={}", cs.conferenceId(), cs.eventId(),
                            botId);
                    return botService.connectBot(cs.conferenceId(), cs.eventId(), botId);
                })
                .onErrorResume(e -> {
                    log.warn("Connect bot from unregistered session or error: sessionId={}", session.getId());
                    return Mono.empty();
                });
    }

    private Mono<Void> handleDisconnectBot(final WebSocketSession session, final JsonNode jsonNode) {
        return sessionService.getSessionData(session)
                .flatMap(cs -> {
                    final String botId = jsonNode.path("botId").asText();
                    if (!StringUtils.hasText(botId)) {
                        log.warn("Empty botId: conferenceId={}, eventId={}", cs.conferenceId(), cs.eventId());
                        return Mono.<Void>empty();
                    }
                    log.info("Disconnect bot: conferenceId={}, eventId={}, botId={}", cs.conferenceId(), cs.eventId(),
                            botId);
                    return botService.disconnectBot(cs.conferenceId(), cs.eventId(), botId);
                })
                .onErrorResume(e -> {
                    log.warn("Disconnect bot from unregistered session or error: sessionId={}", session.getId());
                    return Mono.empty();
                });
    }

    @SuppressWarnings("null")
    private @NonNull Mono<Void> registerFromQuery(final WebSocketSession session) {
        final var queryParams = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri())
                .build()
                .getQueryParams();

        final String clientId = queryParams.getFirst(CLIENT_ID_PARAM);
        final String eventId = queryParams.getFirst(EVENT_ID_PARAM);

        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(eventId)) {
            return Mono.empty();
        }

        return audioDumpService.start(session.getId(), clientId, eventId)
                .then(sessionService.register(session, clientId, eventId))
                .doOnSuccess(v -> log.info("Auto-registered from query: clientId={}, eventId={}", clientId, eventId))
                .then();
    }

    private void handleDisconnect(final WebSocketSession session) {
        audioDumpService.stop(session.getId()) // Завершаем сохранение аудио
                .then(sessionService.getSessionData(session)
                        .flatMap(cs -> sttRoutingService.notifyEnd(cs.conferenceId(), cs.eventId()))
                        .then(sessionService.unregister(session)))
                .doOnSuccess(v -> log.info("Client disconnected: sessionId={}", session.getId()))
                .subscribe();
    }
}
