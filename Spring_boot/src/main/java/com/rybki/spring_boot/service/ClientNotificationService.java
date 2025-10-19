package com.rybki.spring_boot.service;

import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rybki.spring_boot.model.domain.Idea;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientNotificationService {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendIdeaToClient(String eventId, String clientId, Idea payload) {
        Mono<WebSocketSession> sessionMono = sessionService.getSession(eventId, clientId);
        if (sessionMono == null) {
            log.warn("Session (eventId, clientId) = ({}, {}) not found", eventId, clientId);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(payload);

            sessionMono
                .filter(WebSocketSession::isOpen)
                .flatMap(session ->
                    session.send(Mono.just(session.textMessage(json)))
                        .timeout(Duration.ofSeconds(5))
                )
                .doOnSuccess(v ->
                    log.debug("Idea sent to client {}: {}", clientId, payload.id())
                )
                .doOnError(e ->
                    log.error("Failed to send idea to client {} in event {}", clientId, eventId, e)
                )
                .subscribe();

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize idea for client {}", clientId, e);
        }
    }

    // TODO: add other methods to send data out of backend
}
