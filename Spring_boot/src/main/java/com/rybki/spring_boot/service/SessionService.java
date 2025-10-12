package com.rybki.spring_boot.service;

import com.rybki.spring_boot.model.domain.ClientSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SessionService {

    private final ConcurrentMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

    // Регистрирует новую WS-сессию
    public void register(WebSocketSession session, String clientId, String eventId) {
        sessions.put(session.getId(), new ClientSession(clientId, eventId, session));
        log.debug("Registered session: sessionId={}, clientId={}, eventId={}",
            session.getId(), clientId, eventId);
    }

    // Удаляет WS-сессию
    public void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
        log.debug("Unregistered session: sessionId={}", session.getId());
    }

    // Получить clientId по сессии
    public String getClientIdBySession(WebSocketSession session) {
        ClientSession cs = sessions.get(session.getId());
        return cs != null ? cs.clientId() : null;
    }

    // Получить eventId по сессии
    public String getEventIdBySession(WebSocketSession session) {
        ClientSession cs = sessions.get(session.getId());
        return cs != null ? cs.eventId() : null;
    }

    // Получить все сессии для какого то event (например для рассылки сообщений/идей всем участникам события)
    public List<ClientSession> getSessionsForEvent(String eventId) {
        return sessions.values().stream()
            .filter(cs -> cs.eventId().equals(eventId))
            .collect(Collectors.toList());
    }

    // Получить WS-сессию по eventId и clientId
    public WebSocketSession getSession(String eventId, String clientId) {
        return sessions.values().stream()
            .filter(cs -> cs.eventId().equals(eventId) && cs.clientId().equals(clientId))
            .map(ClientSession::session)
            .findFirst()
            .orElse(null);
    }
}
