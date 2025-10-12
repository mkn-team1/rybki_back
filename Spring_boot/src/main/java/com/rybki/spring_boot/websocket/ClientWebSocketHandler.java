package com.rybki.spring_boot.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rybki.spring_boot.service.SessionService;
import com.rybki.spring_boot.service.SttRoutingService;
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
public class ClientWebSocketHandler implements WebSocketHandler {

    private final SessionService sessionService;
    private final SttRoutingService sttRoutingService;
    private final ObjectMapper objectMapper;

    public ClientWebSocketHandler(SessionService sessionService,
                                  SttRoutingService sttRoutingService) {
        this.sessionService = sessionService;
        this.sttRoutingService = sttRoutingService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public @NotNull Mono<Void> handle(@org.jetbrains.annotations.NotNull WebSocketSession session) {
        log.info("Client connected: sessionId={}", session.getId());

        return session.receive()
            .flatMap(message -> switch (message.getType()) {
                case TEXT -> handleTextMessage(session, message);
                case BINARY -> handleBinaryMessage(session, message);
                default -> Mono.empty();
            })
            .doFinally(sig -> handleDisconnect(session))
            .then();
    }

    private Mono<Void> handleTextMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message.getPayloadAsText());
            String type = jsonNode.path("type").asText();

            if ("start".equals(type)) {
                String clientId = jsonNode.has("clientId") ?
                    jsonNode.get("clientId").asText() :
                    UUID.randomUUID().toString();

                String eventId = jsonNode.has("eventId") ?
                    jsonNode.get("eventId").asText() :
                    UUID.randomUUID().toString();

                sessionService.register(session, clientId, eventId);
                log.info("Start received: clientId={}, eventId={}, sessionId={}",
                    clientId, eventId, session.getId());

            } else if ("end".equals(type)) {
                String clientId = sessionService.getClientIdBySession(session);
                String eventId = sessionService.getEventIdBySession(session);
                if (clientId != null && eventId != null) {
                    sttRoutingService.notifyEnd(clientId, eventId);
                }
                sessionService.unregister(session);
                log.info("End received: sessionId={}", session.getId());

            } else {
                log.warn("Unknown message type from client: {}", type);
            }

        } catch (Exception e) {
            log.error("Failed to handle text message", e);
        }

        return Mono.empty();
    }

    private Mono<Void> handleBinaryMessage(WebSocketSession session, WebSocketMessage message) {
        byte[] pcmBytes = message.getPayload().asByteBuffer().array();

        String clientId = sessionService.getClientIdBySession(session);
        String eventId = sessionService.getEventIdBySession(session);

        if (clientId != null && eventId != null) {
            sttRoutingService.forwardAudio(clientId, eventId, pcmBytes);
        } else {
            log.warn("Session not registered for binary message: sessionId={}", session.getId());
        }

        return Mono.empty();
    }

    private void handleDisconnect(WebSocketSession session) {
        String clientId = sessionService.getClientIdBySession(session);
        String eventId = sessionService.getEventIdBySession(session);

        if (clientId != null && eventId != null) {
            sttRoutingService.notifyEnd(clientId, eventId);
        }

        sessionService.unregister(session);
        log.info("Client disconnected: sessionId={}", session.getId());
    }
}
