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
import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ClientNotificationService clientNotificationService;

    // sessionId -> ClientSession
    private final ConcurrentMap<String, ClientSession> clientSessions = new ConcurrentHashMap<>();

    // botId -> WebSocketSession
    private final ConcurrentMap<String, WebSocketSession> botSessions = new ConcurrentHashMap<>();

    // conferenceId <-> botId
    private final ConcurrentMap<String, String> clientToBot = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> botToClient = new ConcurrentHashMap<>();

    public Mono<Void> registerClient(final WebSocketSession session, final String conferenceId,
            final String eventId) {
        return registerClient(session, null, conferenceId, "", eventId)
                .doOnSuccess(v -> log.info("Registered client: conferenceId={}, eventId={}", conferenceId, eventId));
    }

    public Mono<Void> registerClient(final WebSocketSession session, final String conferenceId,
            final String conferenceName, final String eventId) {
        return registerClient(session, null, conferenceId, conferenceName, eventId);
    }

    public Mono<Void> registerClient(final WebSocketSession session, final String clientId, final String conferenceId,
            final String conferenceName, final String eventId) {
        return Mono.fromRunnable(() -> {
            clientSessions.put(session.getId(), ClientSession.builder().clientId(clientId).conferenceId(conferenceId)
                    .conferenceName(conferenceName).eventId(eventId).session(session).build());
            log.info("Registered session: sessionId={}, clientId={}, conferenceId={}, eventId={}",
                    session.getId(), clientId, conferenceId, eventId);
        }).then(notifyParticipantsCountChange(conferenceId));
    }

    public Mono<Void> unregisterClient(final WebSocketSession session) {
        return Mono.fromCallable(() -> {
            final ClientSession cs = clientSessions.remove(session.getId());
            log.debug("Client unregistered: sessionId={}", session.getId());
            return cs;
        }).flatMap(cs -> {
            if (cs != null) {
                return notifyParticipantsCountChange(cs.getConferenceId());
            }
            return Mono.empty();
        });
    }

    public Mono<ClientSession> getSessionData(final WebSocketSession session) {
        ClientSession cs = clientSessions.get(session.getId());
        if (cs == null) {
            log.warn("No ClientSession found for sessionId={}", session.getId());
        } else {
            log.info("Retrieved ClientSession: sessionId={}, conferenceId={}, eventId={}",
                    session.getId(), cs.getConferenceId(), cs.getEventId());
        }
        return Mono.justOrEmpty(cs);
    }

    // Получить все сессии для конкретного event
    public Flux<ClientSession> getSessionsForEvent(final String eventId) {
        final List<ClientSession> list = clientSessions.values().stream()
                .filter(cs -> cs.getEventId().equals(eventId))
                .toList();
        return Flux.fromIterable(list);
    }

    // Получить все сессии для конкретной конференции
    public Flux<ClientSession> getSessionsForConference(final String conferenceId) {
        final List<ClientSession> list = clientSessions.values().stream()
                .filter(cs -> cs.getConferenceId().equals(conferenceId))
                .toList();
        log.debug("Found {} sessions for conferenceId={}", list.size(), conferenceId);
        return Flux.fromIterable(list);
    }

    // Получить количество участников в конференции
    public int getParticipantsCountForConference(final String conferenceId) {
        return (int) clientSessions.values().stream()
                .filter(cs -> cs.getConferenceId().equals(conferenceId))
                .count();
    }

    // Отправить уведомление об изменении количества участников
    private Mono<Void> notifyParticipantsCountChange(final String conferenceId) {
        final int count = getParticipantsCountForConference(conferenceId);
        log.info("📢 [SESSION] Participants count changed for conferenceId={}: count={}", conferenceId, count);
        return clientNotificationService.broadcastParticipantsCount(conferenceId, count);
    }

    // Получить WS-сессию по eventId и conferenceId
    // public Mono<WebSocketSession> getSession(final String eventId, final String conferenceId) {
    //     return Mono.justOrEmpty(
    //             clientSessions.values().stream()
    //                     .filter(cs -> cs.getEventId().equals(eventId) && cs.getConferenceId().equals(conferenceId))
    //                     .map(ClientSession::getSession)
    //                     .findFirst());
    // }
                    
    public Mono<ClientSession> getClientSession(final String conferenceId) {
        return Mono.justOrEmpty(
                clientSessions.values().stream()
                        .filter(cs -> cs.getConferenceId().equals(conferenceId))
                        .findFirst());
    }
    
    // bot logic
    public Mono<Void> registerBot(final String botId, final WebSocketSession session) {
        return Mono.fromRunnable(() -> {
            botSessions.put(botId, session);
            log.debug("Bot registered: botId={}, sessionId={}", botId, session.getId());
        });
    }

    public Mono<Void> unregisterBot(final String botId) {
        return Mono.fromRunnable(() -> {
            botSessions.remove(botId);
            String conferenceId = botToClient.remove(botId);
            if (conferenceId != null) {
                clientToBot.remove(conferenceId);
            }
            log.debug("Bot unregistered: botId={}", botId);
        });
    }

    public WebSocketSession getBotSession(final String botId) {
        return botSessions.get(botId);
    }

    // linking client <-> bot

    public void linkClientAndBot(final String conferenceId, final String botId) {
        clientToBot.put(conferenceId, botId);
        botToClient.put(botId, conferenceId);
    }

    public String getBotForClient(final String conferenceId) {
        return clientToBot.get(conferenceId);
    }

    public String getClientForBot(final String botId) {
        return botToClient.get(botId);
    }
}
