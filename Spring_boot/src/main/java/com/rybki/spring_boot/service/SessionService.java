package com.rybki.spring_boot.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.rybki.spring_boot.model.domain.ClientSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class SessionService {

    // sessionId -> ClientSession
    private final ConcurrentMap<String, ClientSession> clientSessions = new ConcurrentHashMap<>();

    // botId -> WebSocketSession
    private final ConcurrentMap<String, WebSocketSession> botSessions = new ConcurrentHashMap<>();

    // clientId <-> botId
    private final ConcurrentMap<String, String> clientToBot = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> botToClient = new ConcurrentHashMap<>();


    // client logic

    public Mono<Void> register(WebSocketSession session, String clientId, String eventId) {
        return Mono.fromRunnable(() -> {
            clientSessions.put(session.getId(), new ClientSession(clientId, eventId, session));
            log.debug("Client registered: clientId={}, eventId={}, sessionId={}",
                clientId, eventId, session.getId());
        });
    }

    public Mono<Void> unregister(WebSocketSession session) {
        return Mono.fromRunnable(() -> {
            clientSessions.remove(session.getId());
            log.debug("Client unregistered: sessionId={}", session.getId());
        });
    }

    public Mono<ClientSession> getSessionData(WebSocketSession session) {
        return Mono.justOrEmpty(clientSessions.get(session.getId()));
    }

    public Flux<ClientSession> getSessionsForEvent(String eventId) {
        return Flux.fromStream(clientSessions.values().stream()
            .filter(cs -> cs.eventId().equals(eventId)));
    }

    public Mono<WebSocketSession> getClientSession(String clientId, String eventId) {
        return Mono.justOrEmpty(
            clientSessions.values().stream()
                .filter(cs -> cs.clientId().equals(clientId) && cs.eventId().equals(eventId))
                .map(ClientSession::session)
                .findFirst()
        );
    }

    // bot logic

    public Mono<Void> registerBot(String botId, WebSocketSession session) {
        return Mono.fromRunnable(() -> {
            botSessions.put(botId, session);
            log.debug("Bot registered: botId={}, sessionId={}", botId, session.getId());
        });
    }

    public Mono<Void> unregisterBot(String botId) {
        return Mono.fromRunnable(() -> {
            botSessions.remove(botId);
            String clientId = botToClient.remove(botId);
            if (clientId != null) {
                clientToBot.remove(clientId);
            }
            log.debug("Bot unregistered: botId={}", botId);
        });
    }

    public WebSocketSession getBotSession(String botId) {
        return botSessions.get(botId);
    }

    // linking client <-> bot

    public void linkClientAndBot(String clientId, String botId) {
        clientToBot.put(clientId, botId);
        botToClient.put(botId, clientId);
    }

    public String getBotForClient(String clientId) {
        return clientToBot.get(clientId);
    }

    public String getClientForBot(String botId) {
        return botToClient.get(botId);
    }
}
