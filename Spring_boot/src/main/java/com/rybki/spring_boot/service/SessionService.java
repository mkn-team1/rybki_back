package com.rybki.spring_boot.service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.rybki.spring_boot.model.domain.ClientSession;
import com.rybki.spring_boot.model.domain.ConferenceInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    // sessionId -> ClientSession
    private final ConcurrentMap<String, ClientSession> clientSessions = new ConcurrentHashMap<>();

    // botId -> WebSocketSession
    private final ConcurrentMap<String, WebSocketSession> botSessions = new ConcurrentHashMap<>();

    // conferenceId <-> botId
    private final ConcurrentMap<String, String> conferenceToBot = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> botToConference = new ConcurrentHashMap<>();

    // bot WebSocketSession -> mic muted state
    private final ConcurrentMap<WebSocketSession, Boolean> micMuted = new ConcurrentHashMap<>();

    // Регистрирует новую WS-сессию клиента
    public Mono<Void> registerClient(final WebSocketSession session, final String clientId, final String conferenceId,
            final String conferenceName, final String eventId) {
        return Mono.fromRunnable(() -> {
            clientSessions.put(session.getId(), ClientSession.builder().clientId(clientId).conferenceId(conferenceId)
                    .conferenceName(conferenceName).eventId(eventId).session(session).build());
            log.info("Registered session: sessionId={}, clientId={}, conferenceId={}, conferenceName={}, eventId={}",
                    session.getId(), clientId, conferenceId, conferenceName, eventId);
        });
    }

    public Mono<Void> unregisterClient(final WebSocketSession session) {
        return Mono.fromCallable(() -> {
            final ClientSession cs = clientSessions.remove(session.getId());
            log.debug("Client unregistered: sessionId={}", session.getId());
            return cs;
        }).then();
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

    // Получить WS-сессию по eventId и conferenceId
    // public Mono<WebSocketSession> getSession(final String eventId, final String conferenceId) {
    //     return Mono.justOrEmpty(
    //             clientSessions.values().stream()
    //                     .filter(cs -> cs.getEventId().equals(eventId) && cs.getConferenceId().equals(conferenceId))
    //                     .map(ClientSession::getSession)
    //                     .findFirst());
    // }
                    
    public Mono<ClientSession> getClientSession(final String clientId) {
        return Mono.justOrEmpty(
                clientSessions.values().stream()
                        .filter(cs -> cs.getClientId().equals(clientId))
                        .findFirst());
    }

    public Mono<ConferenceInfo> getConferenceInfo(final String conferenceId) {
        return Mono.justOrEmpty(clientSessions.values().stream()
            .filter(cs -> cs.getConferenceId().equals(conferenceId))
            .map(cs -> ConferenceInfo.builder()
                    .conferenceId(cs.getConferenceId())
                    .conferenceName(cs.getConferenceName())
                    .eventId(cs.getEventId())
                    .build())
            .findFirst().orElse(null));
    }
    
    // bot logic
    public Mono<Void> registerBot(final String botId, final WebSocketSession session) {
        return Mono.fromRunnable(() -> {
            botSessions.put(botId, session);
            micMuted.put(session, false);
            log.debug("Bot registered: botId={}, sessionId={}", botId, session.getId());
        });
    }

    public Mono<Void> unregisterBot(final String botId) {
        return Mono.fromRunnable(() -> {
            WebSocketSession session = botSessions.get(botId);
            if (session != null) {
                micMuted.remove(session);
            }
            botSessions.remove(botId);
            String conferenceId = botToConference.remove(botId);
            if (conferenceId != null) {
                conferenceToBot.remove(conferenceId);
            }
            log.debug("Bot unregistered: botId={}", botId);
        });
    }

    public void unlinkBot(final String botId) {
        String conferenceId = botToConference.remove(botId);
        if (conferenceId != null) {
            conferenceToBot.remove(conferenceId);
        }
    }

    public WebSocketSession getBotSession(final String botId) {
        return botSessions.get(botId);
    }

    // linking conference <-> bot

    public void linkConferenceAndBot(final String conferenceId, final String botId) {
        conferenceToBot.put(conferenceId, botId);
        botToConference.put(botId, conferenceId);
    }

    public String getBotForConference(final String conferenceId) {
        return conferenceToBot.get(conferenceId);
    }

    public String getConferenceForBot(final String botId) {
        return botToConference.get(botId);
    }

    public Boolean isBotMicMuted(final String botId) {
        WebSocketSession botSession = botSessions.get(botId);
        if (botSession == null) {
            log.warn("No bot session found for botId={}", botId);
            return false;
        }
        return micMuted.getOrDefault(botSession, false);
    }

    // returns if bot mic is muted after switch
    public Boolean switchBotMic(final String botId) {
        WebSocketSession botSession = botSessions.get(botId);
        if (botSession == null) {
            log.warn("No bot session found for botId={}, cannot switch mic", botId);
            return true;
        }
        Boolean currentState = micMuted.getOrDefault(botSession, false);
        Boolean newState = !currentState;
        micMuted.put(botSession, newState);
        log.info("Switched mic state for bot sessionId={} to {}", botSession.getId(), newState);
        return newState;
    }

}
