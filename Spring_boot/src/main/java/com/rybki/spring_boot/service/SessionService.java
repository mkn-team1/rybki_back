package com.rybki.spring_boot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SessionService {

    // Ключ: eventId::clientId
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private String key(String eventId, String clientId) {
        return eventId + "::" + clientId;
    }

    public void register(WebSocketSession session, String eventId, String clientId) {
        sessions.put(key(eventId, clientId), session);
        log.info("Registered session for eventId={}, clientId={}", eventId, clientId);
    }

    public void unregister(WebSocketSession session) {
        sessions.entrySet().removeIf(e -> e.getValue().equals(session));
        log.info("Unregistered session: {}", session.getId());
    }

    public WebSocketSession getSession(String eventId, String clientId) {
        return sessions.get(key(eventId, clientId));
    }

    // Иногда нужно получить все сессии события (например, для рассылки)
    public Map<String, WebSocketSession> getSessionsForEvent(String eventId) {
        return sessions.entrySet().stream()
            .filter(e -> e.getKey().startsWith(eventId + "::"))
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
