package com.rybki.spring_boot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rybki.spring_boot.model.domain.Idea;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientNotificationService {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Mono<Void> sendIdeaToClient(String clientId, String eventId, Idea idea) {
        return sessionService.getSession(eventId, clientId)
            .flatMap(session -> sendMessage(session, clientId, eventId, idea))
            .doOnError(e -> log.error("Failed to send idea to client: {}", clientId, e))
            .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> sendMessage(WebSocketSession session, String clientId, String eventId, Idea idea) {
        try {
            Map<String, Object> messageMap = Map.of(
                "type", "idea",
                "clientId", clientId,
                "eventId", eventId,
                "idea", idea
            );

            String message = objectMapper.writeValueAsString(messageMap);

            return session.send(Mono.just(session.textMessage(message)))
                .doOnSuccess(v -> log.info("Sent idea to client: clientId={}, ideaId={}", clientId, idea.id()))
                .then();

        } catch (Exception e) {
            log.error("Failed to serialize idea message", e);
            return Mono.empty();
        }
    }
}
