package com.rybki.spring_boot.websocket;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rybki.spring_boot.service.SessionService;
import com.rybki.spring_boot.service.SttRoutingService;
import com.rybki.spring_boot.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientWebSocketHandler implements WebSocketHandler {

    private final SessionService sessionService;
    private final SttRoutingService sttRoutingService;
    private final VoteService voteService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public @NotNull Mono<Void> handle(@NotNull final WebSocketSession session) {
        log.info("Client connected: sessionId={}", session.getId());

        return session.receive()
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

    private Mono<Void> handleStart(final WebSocketSession session, final JsonNode jsonNode) {
        // TODO: Вместо генерации Id, если его нет, нужно обрывать подключение
        final String clientId =
            jsonNode.has("clientId") ? jsonNode.get("clientId").asText() : UUID.randomUUID().toString();
        final String eventId =
            jsonNode.has("eventId") ? jsonNode.get("eventId").asText() : UUID.randomUUID().toString();

        return sessionService.register(session, clientId, eventId)
            .doOnSuccess(v -> log.info("Start: clientId={}, eventId={}", clientId, eventId))
            .then();
    }

    private Mono<Void> handleEnd(final WebSocketSession session) {
        return sessionService.getSessionData(session)
            .flatMap(cs -> sttRoutingService.notifyEnd(cs.clientId(), cs.eventId())
                .then(sessionService.unregister(session))
                .doOnSuccess(v -> log.info("End: sessionId={}", session.getId()))
            )
            .then();
    }

    private Mono<Void> handleVote(final WebSocketSession session, final JsonNode jsonNode) {
        return sessionService.getSessionData(session)
            .flatMap(cs -> voteService.registerVote(cs.clientId(), cs.eventId(), jsonNode)
                .doOnSuccess(v -> log.info("Vote: clientId={}, eventId={}", cs.clientId(), cs.eventId()))
                .then()
            )
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
                return sttRoutingService.forwardAudio(cs.clientId(), cs.eventId(), bytes)
                    .doOnError(
                        e -> log.error("Failed to forward audio: clientId={}, eventId={}", cs.clientId(), cs.eventId(),
                            e));
            })
            .onErrorResume(e -> {
                log.warn("Binary from unregistered session or error: sessionId={}", session.getId());
                return Mono.empty();
            });
    }

    private void handleDisconnect(final WebSocketSession session) {
        sessionService.getSessionData(session)
            .flatMap(cs -> sttRoutingService.notifyEnd(cs.clientId(), cs.eventId()))
            .then(sessionService.unregister(session))
            .doOnSuccess(v -> log.info("Client disconnected: sessionId={}", session.getId()))
            .subscribe();
    }
}
