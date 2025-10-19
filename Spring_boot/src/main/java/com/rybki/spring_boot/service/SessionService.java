package com.rybki.spring_boot.service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.rybki.spring_boot.model.domain.ClientSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Slf4j
@Service
public class SessionService {

    private final ConcurrentMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

    // Регистрирует новую WS-сессию
    public Mono<Void> register(final WebSocketSession session, final String clientId, final String eventId) {
        return Mono.fromRunnable(() -> {
            sessions.put(session.getId(), new ClientSession(clientId, eventId, session));
            log.debug("Registered session: sessionId={}, clientId={}, eventId={}",
                session.getId(), clientId, eventId);
        });
    }

    // Удаляет WS-сессию
    public Mono<Void> unregister(final WebSocketSession session) {
        return Mono.fromRunnable(() -> {
            sessions.remove(session.getId());
            log.debug("Unregistered session: sessionId={}", session.getId());
        });
    }

    // Получить clientId и eventId сразу
    public Mono<ClientSession> getSessionData(final WebSocketSession session) {
        return Mono.justOrEmpty(sessions.get(session.getId()));
    }

    // Получить все сессии для конкретного event
    public Flux<ClientSession> getSessionsForEvent(final String eventId) {
        final List<ClientSession> list = sessions.values().stream()
            .filter(cs -> cs.eventId().equals(eventId))
            .collect(Collectors.toList());
        return Flux.fromIterable(list);
    }

    // Получить WS-сессию по eventId и clientId
    public Mono<WebSocketSession> getSession(final String eventId, final String clientId) {
        return Mono.justOrEmpty(
            sessions.values().stream()
                .filter(cs -> cs.eventId().equals(eventId) && cs.clientId().equals(clientId))
                .map(ClientSession::session)
                .findFirst()
        );
    }
}
