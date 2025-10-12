package com.rybki.spring_boot.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rybki.spring_boot.service.SessionService;
import com.rybki.spring_boot.service.SttRoutingService;
import com.rybki.spring_boot.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.WebSocketMessage;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientWebSocketHandler implements WebSocketHandler {

    private final SessionService sessionService;
    private final SttRoutingService sttRoutingService;
    private final VoteService voteService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public @NotNull Mono<Void> handle(@NotNull WebSocketSession session) {
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

    private Mono<Void> handleTextMessage(WebSocketSession session, WebSocketMessage message) {
        return Mono.fromCallable(() -> objectMapper.readTree(message.getPayloadAsText()))
            .flatMap(jsonNode -> {
                String type = jsonNode.path("type").asText();
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

    private Mono<Void> handleStart(WebSocketSession session, com.fasterxml.jackson.databind.JsonNode jsonNode) {
        String clientId = jsonNode.has("clientId") ? jsonNode.get("clientId").asText() : UUID.randomUUID().toString();
        String eventId = jsonNode.has("eventId") ? jsonNode.get("eventId").asText() : UUID.randomUUID().toString();

        return sessionService.register(session, clientId, eventId)
            .doOnSuccess(v -> log.info("Start: clientId={}, eventId={}", clientId, eventId))
            .then();
    }

    private Mono<Void> handleEnd(WebSocketSession session) {
        return notifyEndIfRegistered(session)
            .then(sessionService.unregister(session)
                .doOnSuccess(v -> log.info("End: sessionId={}", session.getId()))
            );
    }

    private Mono<Void> handleVote(WebSocketSession session, com.fasterxml.jackson.databind.JsonNode jsonNode) {
        return Mono.zip(
                sessionService.getClientIdBySession(session),
                sessionService.getEventIdBySession(session)
            )
            .flatMap(tuple -> voteService.registerVote(tuple.getT1(), tuple.getT2(), jsonNode)
                .doOnSuccess(v -> log.info("Vote: clientId={}, eventId={}", tuple.getT1(), tuple.getT2()))
                .then()
            )
            .onErrorResume(e -> {
                log.warn("Vote from unregistered session or error: sessionId={}", session.getId());
                return Mono.empty();
            });
    }

    private Mono<Void> handleBinaryMessage(WebSocketSession session, WebSocketMessage message) {
        return Mono.zip(
                sessionService.getClientIdBySession(session),
                sessionService.getEventIdBySession(session)
            )
            .flatMap(tuple -> {
                byte[] bytes = new byte[message.getPayload().readableByteCount()];
                message.getPayload().read(bytes);
                return sttRoutingService.forwardAudio(tuple.getT1(), tuple.getT2(), bytes)
                    .doOnError(e -> log.error("Failed to forward audio: clientId={}, eventId={}", tuple.getT1(), tuple.getT2(), e));
            })
            .onErrorResume(e -> {
                log.warn("Binary from unregistered session or error: sessionId={}", session.getId());
                return Mono.empty();
            });
    }

    private void handleDisconnect(WebSocketSession session) {
        notifyEndIfRegistered(session)
            .then(sessionService.unregister(session))
            .doOnSuccess(v -> log.info("Client disconnected: sessionId={}", session.getId()))
            .subscribe();
    }

    private Mono<Void> notifyEndIfRegistered(WebSocketSession session) {
        return Mono.zip(
                sessionService.getClientIdBySession(session),
                sessionService.getEventIdBySession(session)
            )
            .flatMap(tuple -> sttRoutingService.notifyEnd(tuple.getT1(), tuple.getT2()))
            .then();
    }
}
