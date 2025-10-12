package com.rybki.spring_boot.service;

import com.rybki.spring_boot.model.domain.ClientSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SessionService {

    // key = clientId + ":" + eventId
    private final ConcurrentHashMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

    /**
     * Зарегистрировать новую сессию
     */
    public void register(WebSocketSession session, String clientId, String eventId) {
        String key = makeKey(clientId, eventId);
        ClientSession clientSession = new ClientSession(clientId, eventId, session);
        sessions.put(key, clientSession);
        log.info("Registered session: clientId={}, eventId={}, sessionId={}", clientId, eventId, session.getId());
    }

    /**
     * Удалить сессию
     */
    public void unregister(WebSocketSession session) {
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().getSession().getId().equals(session.getId())) {
                log.info("Unregistered session: clientId={}, eventId={}, sessionId={}",
                    entry.getValue().getClientId(),
                    entry.getValue().getEventId(),
                    session.getId());
                return true;
            }
            return false;
        });
    }

    /**
     * Получить конкретную сессию по clientId и eventId
     */
    public ClientSession getSession(String eventId, String clientId) {
        return sessions.get(makeKey(clientId, eventId));
    }

    /**
     * Получить все сессии для event
     */
    public List<ClientSession> getSessionsForEvent(String eventId) {
        return sessions.values().stream()
            .filter(cs -> cs.getEventId().equals(eventId))
            .collect(Collectors.toList());
    }

    /**
     * Вспомогательный ключ
     */
    private String makeKey(String clientId, String eventId) {
        return clientId + ":" + eventId;
    }

    /**
     * Получить clientId по WebSocketSession
     */
    public String getClientIdBySession(WebSocketSession session) {
        return sessions.values().stream()
            .filter(cs -> cs.getSession().getId().equals(session.getId()))
            .map(ClientSession::getClientId)
            .findFirst().orElse(null);
    }

    /**
     * Получить eventId по WebSocketSession
     */
    public String getEventIdBySession(WebSocketSession session) {
        return sessions.values().stream()
            .filter(cs -> cs.getSession().getId().equals(session.getId()))
            .map(ClientSession::getEventId)
            .findFirst().orElse(null);
    }
}
