package com.rybki.spring_boot.service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.rybki.spring_boot.model.domain.ClientSession;

import lombok.extern.slf4j.Slf4j;
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

    // Регистрирует новую WS-сессию
    public Mono<Void> register(final WebSocketSession session, final String conferenceId,
            final String eventId) {
        return register(session, conferenceId, "", eventId);
    }

    public Mono<Void> register(final WebSocketSession session, final String conferenceId,
            final String conferenceName, final String eventId) {
        return Mono.fromRunnable(() -> {
            clientSessions.put(session.getId(),
                    new ClientSession(conferenceId, conferenceName, eventId, session));
            log.debug("Registered session: sessionId={}, conferenceId={}, eventId={}",
                    session.getId(), conferenceId, eventId);

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

    // Получить все сессии для конкретного event
    public Flux<ClientSession> getSessionsForEvent(final String eventId) {
        final List<ClientSession> list = sessions.values().stream()
                .filter(cs -> cs.eventId().equals(eventId))
                .toList();
        return Flux.fromIterable(list);
    }

    // Получить WS-сессию по eventId и conferenceId
    public Mono<WebSocketSession> getSession(final String eventId, final String conferenceId) {
        return Mono.justOrEmpty(
                sessions.values().stream()
                        .filter(cs -> cs.eventId().equals(eventId) && cs.conferenceId().equals(conferenceId))
                        .map(ClientSession::session)
                        .findFirst());

     public Mono<ClientSession> getClientSession(String clientId) {
        return Mono.justOrEmpty(
                clientSessions.values().stream()
                        .filter(cs -> cs.clientId().equals(clientId))
                        .findFirst());
    }

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
