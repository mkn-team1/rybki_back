package com.rybki.spring_boot.websocket;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rybki.spring_boot.service.BotService;
import com.rybki.spring_boot.service.IdeaService;
import com.rybki.spring_boot.service.SessionService;
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
    private final VoteService voteService;

    private final IdeaService ideaService;
    private final BotService botService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @SuppressWarnings("null")
    public @NonNull Mono<Void> handle(@NonNull final WebSocketSession session) {
        log.info("Client connected: sessionId={}", session.getId());

        final var queryParams = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri())
                .build()
                .getQueryParams();

        final String eventId = queryParams.getFirst(EVENT_ID_PARAM);
        final String conferenceId = queryParams.getFirst(CLIENT_ID_PARAM);

        if (!StringUtils.hasText(conferenceId) || !StringUtils.hasText(eventId)) {
            return session.close();
        }

        // return sessionService.registerClient(session, conferenceId, eventId)
        //         .thenMany(session.receive())
        //         .flatMap(message -> switch (message.getType()) {
        //             case TEXT -> handleTextMessage(session, message);
        //             // case BINARY -> handleBinaryMessage(session, message);
        //             default -> Mono.empty();
        //         })
        //         .then(handleClientDisconnect(session, conferenceId));

        Mono<Void> register = sessionService.registerClient(session, conferenceId, eventId);

        Mono<Void> messages = session.receive()
            .flatMap(msg -> switch (msg.getType()) {
                case TEXT -> handleTextMessage(session, msg);
                // case BINARY -> handleBinaryMessage(session, message);
                default   -> Mono.empty();
            })
            .then() 
            .doFinally(signalType -> {
                log.info("WS finished: conferenceId={}, sessionId={}, signal={}",
                        conferenceId, session.getId(), signalType);
                botService.disconnectBot(conferenceId)
                    .then(sessionService.unregisterClient(session))
                    .doOnSuccess(v -> log.info(
                        "Client disconnected: conferenceId={}, sessionId={}",
                        conferenceId, session.getId()
                    ))
                    .subscribe();
            });

        return register.then(messages);
    }

    private Mono<Void> handleTextMessage(final WebSocketSession session, final WebSocketMessage message) {
        return Mono.fromCallable(() -> objectMapper.readTree(message.getPayloadAsText()))
                .flatMap(jsonNode -> {
                    final String type = jsonNode.path("type").asText();
                    return switch (type) {
                        // case "start" -> handleStart(session, jsonNode);
                        // case "end" -> handleEnd(session);
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

    // @SuppressWarnings("null")
    // private Mono<Void> handleStart(final WebSocketSession session, final JsonNode jsonNode) {
    //     if (!jsonNode.has(CLIENT_ID_PARAM) || !jsonNode.has(EVENT_ID_PARAM)) {
    //         log.warn("Missing clientId/eventId in start message: sessionId={}", session.getId());
    //         return session.close(CloseStatus.BAD_DATA);
    //     }

    //     final String conferenceId = jsonNode.get(CLIENT_ID_PARAM).asText();
    //     final String eventId = jsonNode.get(EVENT_ID_PARAM).asText();

    //     return sessionService.registerClient(session, conferenceId, eventId)
    //             .doOnSuccess(v -> log.info("Start: conferenceId={}, eventId={}", conferenceId, eventId))
    //             .then();
    // }

    // private Mono<Void> handleEnd(final WebSocketSession session) {
    //     return sessionService.getSessionData(session)
    //                     .flatMap(cs -> sttRoutingService.notifyEnd(cs.getConferenceId(), cs.getEventId())
    //                             .then(sessionService.unregisterClient(session))
    //                             .doOnSuccess(v -> log.info("End: sessionId={}", session.getId())));
    // }

    private Mono<Void> handleVote(final WebSocketSession session, final JsonNode jsonNode) {
        return sessionService.getSessionData(session)
                .flatMap(cs -> voteService.registerVote(cs.getConferenceId(), cs.getEventId(), jsonNode)
                        .doOnSuccess(v -> log.info("Vote: conferenceId={}, eventId={}", cs.getConferenceId(),
                                cs.getEventId()))
                        .then())
                .onErrorResume(e -> {
                    log.warn("Vote from unregistered session or error: sessionId={}", session.getId());
                    return Mono.empty();
                });
    }

    // private Mono<Void> handleBinaryMessage(final WebSocketSession session, final WebSocketMessage message) {
    //     return sessionService.getSessionData(session)
    //             .flatMap(cs -> {
    //                 final byte[] bytes = new byte[message.getPayload().readableByteCount()];
    //                 message.getPayload().read(bytes);
    //                 return audioDumpService.append(session.getId(), bytes)
    //                         .then(sttRoutingService.forwardAudio(cs.getConferenceId(), cs.getEventId(), bytes))
    //                         .doOnError(
    //                                 e -> log.error("Failed to forward audio: conferenceId={}, eventId={}",
    //                                         cs.getConferenceId(), cs.getEventId(), e));
    //             })
    //             .onErrorResume(e -> {
    //                 log.warn("Binary from unregistered session or error: sessionId={}", session.getId());
    //                 return Mono.empty();
    //             });
    // }

    private Mono<Void> handleCreateIdea(final WebSocketSession session, final JsonNode jsonNode) {
        return sessionService.getSessionData(session)
                .flatMap(cs -> {
                    final JsonNode dataNode = jsonNode.path("data");
                    final String ideaTitle = dataNode.path("title").asText();
                    final String ideaDescription = dataNode.path("description").asText("");

                    if (!StringUtils.hasText(ideaTitle)) {
                        log.warn("Empty idea title: conferenceId={}, eventId={}", cs.getConferenceId(), cs.getEventId());
                        return Mono.<Void>empty();
                    }
                    log.info("Create idea: conferenceId={}, eventId={}, title={}", cs.getConferenceId(), cs.getEventId(),
                            ideaTitle);
                    return ideaService.createIdeaFromFront(cs.getConferenceId(), cs.getConferenceName(), cs.getEventId(),
                            ideaTitle, ideaDescription);
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
                        log.warn("Empty ideaId: conferenceId={}, eventId={}", cs.getConferenceId(), cs.getEventId());
                        return Mono.<Void>empty();
                    }
                    log.info("Delete idea: conferenceId={}, eventId={}, ideaId={}", cs.getConferenceId(), cs.getEventId(),
                            ideaId);
                    return ideaService.deleteIdea(cs.getConferenceId(), cs.getEventId(), ideaId);
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
                        log.warn("Empty ideaId: conferenceId={}, eventId={}", cs.getConferenceId(), cs.getEventId());
                        return Mono.<Void>empty();
                    }
                    log.info("React to idea: conferenceId={}, eventId={}, ideaId={}, reaction={}", cs.getConferenceId(),
                            cs.getEventId(), ideaId, reactionType);
                    return ideaService.reactToIdea(cs.getConferenceId(), cs.getEventId(), ideaId, reactionType);
                })
                .onErrorResume(e -> {
                    log.warn("React to idea from unregistered session or error: sessionId={}", session.getId());
                    return Mono.empty();
                });
    }

    private Mono<Void> handleConnectBot(final WebSocketSession session, final JsonNode jsonNode) {
        return sessionService.getSessionData(session)
                .flatMap(cs -> {
                    final String talkLink = jsonNode.path("talkLink").asText();
                    final String platform = "kontur_talk"; // Пока только kontur_talk

                    // TODO: во первых добавить проверку ссылки, во вторых придумать как возвращать ошибку, 
                    // если ссылка неправильная (и изменить на meetingUrl)

                    log.info("Connect bot request: conferenceId={}, eventId={}, talkLink={}, platform={}",
                            cs.getConferenceId(), cs.getEventId(), talkLink, platform);

                    return botService.createBot(cs.getConferenceId(), talkLink, platform);
                })
                .onErrorResume(e -> {
                    log.error("Failed to send connect bot command: sessionId={}, error={}",
                            session.getId(), e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> handleDisconnectBot(final WebSocketSession session, final JsonNode jsonNode) {
        return sessionService.getSessionData(session)
                .flatMap(cs -> {
                    return botService.disconnectBot(cs.getConferenceId());
                })
                .onErrorResume(e -> {
                    log.warn("Disconnect bot from unregistered session or error: sessionId={}", session.getId());
                    return Mono.empty();
                });
    }
}
