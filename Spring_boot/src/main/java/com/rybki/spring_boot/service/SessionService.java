package com.rybki.spring_boot.service;

import com.rybki.spring_boot.model.domain.ClientSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SessionService {

    private final ConcurrentMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

    // Регистрирует новую WS-сессию
    public Mono<Void> register(WebSocketSession session, String clientId, String eventId) {
        return Mono.fromRunnable(() -> {
            sessions.put(session.getId(), new ClientSession(clientId, eventId, session));
            log.debug("Registered session: sessionId={}, clientId={}, eventId={}",
                session.getId(), clientId, eventId);
        });
    }

    // Удаляет WS-сессию
    public Mono<Void> unregister(WebSocketSession session) {
        return Mono.fromRunnable(() -> {
            sessions.remove(session.getId());
            log.debug("Unregistered session: sessionId={}", session.getId());
        });
    }

    // Получить clientId по сессии
    public Mono<String> getClientIdBySession(WebSocketSession session) {
        ClientSession cs = sessions.get(session.getId());
        return Mono.justOrEmpty(cs != null ? cs.clientId() : null);
    }

    // Получить eventId по сессии
    public Mono<String> getEventIdBySession(WebSocketSession session) {
        ClientSession cs = sessions.get(session.getId());
        return Mono.justOrEmpty(cs != null ? cs.eventId() : null);
    }

    // Получить все сессии для конкретного event
    public Flux<ClientSession> getSessionsForEvent(String eventId) {
        List<ClientSession> list = sessions.values().stream()
            .filter(cs -> cs.eventId().equals(eventId))
            .collect(Collectors.toList());
        return Flux.fromIterable(list);
    }

    // Получить WS-сессию по eventId и clientId
    public Mono<WebSocketSession> getSession(String eventId, String clientId) {
        return Mono.justOrEmpty(
            sessions.values().stream()
                .filter(cs -> cs.eventId().equals(eventId) && cs.clientId().equals(clientId))
                .map(ClientSession::session)
                .findFirst()
        );
    }
}
