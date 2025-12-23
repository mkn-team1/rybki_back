package com.rybki.spring_boot.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rybki.spring_boot.model.domain.redis.Idea;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientNotificationService {

        @Lazy
        private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    /**
     * Send a new idea to a specific client (not broadcast to all)
     */
    public Mono<Void> sendIdeaToClient(final String conferenceId, final Idea idea) {
        return sendToClient(conferenceId, Map.of(
                "type", "idea",
                "idea", idea));
    }

    /**
     * Broadcast a new idea to all participants within the same conference
     */
    public Mono<Void> broadcastIdeaToConference(final String conferenceId, final String eventId, final Idea idea) {
        log.info("📢 [BROADCAST] broadcastIdeaToConference: conferenceId={}, idea.conferenceId={}, idea.conferenceName='{}'",
                conferenceId, idea.getConferenceId(), idea.getConferenceName());
        return broadcastToConference(conferenceId, Map.of(
                "type", "idea",
                "conferenceId", conferenceId,
                "eventId", eventId,
                "idea", idea.prepareForSerialization()));
    }

    /**
     * Broadcast a new idea to all connected clients for an event (excluding source conference)
     */
    public Mono<Void> broadcastIdea(final String conferenceId, final String eventId, final Idea idea) {
        return broadcastToEvent(eventId, conferenceId, Map.of(
                "type", "idea",
                "conferenceId", conferenceId,
                "eventId", eventId,
                "idea", idea.prepareForSerialization()));
    }

    /**
     * Broadcast idea deletion to all connected clients for an event
     */
    public Mono<Void> broadcastIdeaDeleted(final String conferenceId, final String eventId, final String ideaId) {
        return broadcastToEvent(eventId, conferenceId, Map.of(
                "type", "idea_deleted",
                "conferenceId", conferenceId,
                "eventId", eventId,
                "ideaId", ideaId));
    }

    /**
     * Broadcast idea reaction update to all connected clients in the conference
     */
    public Mono<Void> broadcastIdeaReaction(final String conferenceId, final String ideaId,
            final int likes, final int dislikes) {
        return broadcastToConference(conferenceId, Map.of(
                "type", "idea_reaction",
                "ideaId", ideaId,
                "likes", likes,
                "dislikes", dislikes));
    }

    /**
     * Broadcast idea reaction update to all connected clients in the event (for GLOBAL ideas)
     */
    public Mono<Void> broadcastIdeaReactionToEvent(final String eventId, final String ideaId,
            final int likes, final int dislikes) {
        return broadcastToEvent(eventId, "", Map.of(
                "type", "idea_reaction",
                "ideaId", ideaId,
                "likes", likes,
                "dislikes", dislikes));
    }

    /**
     * Broadcast idea status change to all connected clients in the event
     */
    public Mono<Void> broadcastIdeaStatusChanged(final String eventId, final String conferenceId, 
            final String ideaId, final String status) {
        return broadcastToEvent(eventId, conferenceId, Map.of(
                "type", "idea_status_changed",
                "ideaId", ideaId,
                "status", status));
    }

    /**
     * Broadcast list of ideas to all connected clients for an event
     */
    public Mono<Void> broadcastIdeasList(final String conferenceId, final String eventId, final List<Idea> ideas) {
        final List<Idea> preparedIdeas = ideas.stream()
                .map(Idea::prepareForSerialization)
                .toList();
        return broadcastToEvent(eventId, conferenceId, Map.of(
                "type", "ideas_list",
                "conferenceId", conferenceId,
                "eventId", eventId,
                "ideas", preparedIdeas));
    }

    /**
     * Send bot connection notification to a conference
     */
    public Mono<Void> botConnected(final String conferenceId, final String botId) {
        return broadcastToConference(conferenceId, Map.of(
                "type", "bot_connected",
                "botId", botId));
    }

    /**
     * Send bot disconnection notification to a conference
     */
    public Mono<Void> botDisconnected(final String conferenceId, final String botId) {
        return broadcastToConference(conferenceId, Map.of(
                "type", "bot_disconnected",
                "botId", botId));
    }

    /**
     * Send answer on question to a conference
     */
    public Mono<Void> sendQuestionAnswer(final String conferenceId, final String question, final String answer) {
        return broadcastToConference(conferenceId, Map.of(
                "type", "question_answer",
                "question", question,
                "answer", answer));
    }

    /**
     * Send message about current micro state to all conference participants
     */
    public Mono<Void> sendMicSwitchNotification(final String conferenceId, final Boolean micMuted) {
        return broadcastToConference(conferenceId, Map.of(
                "type", "mic_switch",
                "micMuted", micMuted));
    }

    /**
     * Broadcast participants count change to all clients in the conference
     */
    public Mono<Void> broadcastParticipantsCount(final String conferenceId, final int count) {
        return broadcastToConference(conferenceId, Map.of(
                "type", "participants_count",
                "count", count));
    }

    /**
     * Send a message to a specific client by conference ID
     */
    private Mono<Void> sendToClient(final String conferenceId, final Map<String, Object> message) {
        return sessionService.getClientSession(conferenceId)
                .flatMap(session -> {
                    try {
                        final WebSocketSession wsSession = session.getSession();
                        // Пропускаем уже закрытые сессии
                        if (!wsSession.isOpen()) {
                            log.debug("⚠️ [NOTIFICATION] Skipping closed session for conferenceId={}", conferenceId);
                            return Mono.empty();
                        }
                        final String jsonMessage = objectMapper.writeValueAsString(message);
                        @SuppressWarnings("null")
                        Mono<Void> result = wsSession
                                .send(Mono.just(wsSession.textMessage(jsonMessage)))
                                .doOnSuccess(v -> log.info("✅ [NOTIFICATION] Sent message type={} to client "
                                        + "conferenceId={}", message.get("type"), conferenceId))
                                .doOnError(e -> log.error("❌ [NOTIFICATION] Failed to send message to client: {}",
                                        conferenceId, e))
                                .onErrorResume(e -> Mono.empty())
                                .then();
                        return result;
                    } catch (final Exception e) {
                        log.error("❌ [NOTIFICATION] Failed to serialize message", e);
                        return Mono.empty();
                    }
                })
                .doOnError(e -> log.warn("⚠️ [NOTIFICATION] Client not found or error: conferenceId={}",
                        conferenceId))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Broadcast a message to all participants within the same conference
     */
    private Mono<Void> broadcastToConference(final String conferenceId, final Map<String, Object> message) {
        log.info("📡 [BROADCAST-CONFERENCE] Starting broadcast for conferenceId={}, messageType={}", 
                conferenceId, message.get("type"));
        return sessionService.getSessionsForConference(conferenceId)
                .doOnNext(session -> log.debug("📡 [BROADCAST-CONFERENCE] Broadcasting to session: conferenceId={}, sessionId={}", 
                        session.getConferenceId(), session.getSession().getId()))
                .flatMap(clientSession -> {
                    try {
                        final WebSocketSession session = clientSession.getSession();
                        // Пропускаем уже закрытые сессии
                        if (!session.isOpen()) {
                            log.debug("⚠️ [BROADCAST-CONFERENCE] Skipping closed session: {}", session.getId());
                            return Mono.empty();
                        }
                        final String jsonMessage = objectMapper.writeValueAsString(message);
                        @SuppressWarnings("null")
                        final Mono<Void> result = session.send(
                                Mono.just(session.textMessage(jsonMessage)))
                                .doOnError(e -> log.error(
                                        "❌ [BROADCAST-CONFERENCE] Failed to broadcast to session: {}",
                                        session.getId(), e))
                                .onErrorResume(e -> Mono.empty());
                        return result;
                    } catch (final IOException e) {
                        log.error("❌ [BROADCAST-CONFERENCE] Failed to serialize broadcast message", e);
                        return Mono.empty();
                    }
                })
                .collectList()
                .then()
                .doOnSuccess(v -> log.info("✅ [BROADCAST-CONFERENCE] Broadcast message type={} to conference={} completed",
                        message.get("type"), conferenceId))
                .onErrorResume(e -> {
                    log.warn("⚠️ [BROADCAST-CONFERENCE] Error broadcasting to conference={}: {}", conferenceId, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Broadcast a message to all clients connected to an event, excluding specific conference
     */
    private Mono<Void> broadcastToEvent(final String eventId, final String excludeConferenceId, final Map<String, Object> message) {
        log.info("📡 [BROADCAST] Starting broadcast for eventId={}, messageType={}, excluding conferenceId={}", 
                eventId, message.get("type"), excludeConferenceId);
        return sessionService.getSessionsForEvent(eventId)
                .filter(cs -> !cs.getConferenceId().equals(excludeConferenceId))
                .doOnNext(session -> log.debug("📡 [BROADCAST] Broadcasting to session: conferenceId={}, sessionId={}", 
                        session.getConferenceId(), session.getSession().getId()))
                .flatMap(clientSession -> {
                    try {
                        final WebSocketSession session = clientSession.getSession();
                        // Пропускаем уже закрытые сессии
                        if (!session.isOpen()) {
                            log.debug("⚠️ [BROADCAST] Skipping closed session: {}", session.getId());
                            return Mono.empty();
                        }
                        final String jsonMessage = objectMapper.writeValueAsString(message);
                        @SuppressWarnings("null")
                        final Mono<Void> result = session.send(
                                Mono.just(session.textMessage(jsonMessage)))
                                .doOnError(e -> log.error(
                                        "❌ [BROADCAST] Failed to broadcast to session: {}",
                                        session.getId(), e))
                                .onErrorResume(e -> Mono.empty());
                        return result;
                    } catch (final IOException e) {
                        log.error("❌ [BROADCAST] Failed to serialize broadcast message", e);
                        return Mono.empty();
                    }
                })
                .collectList()
                .then()
                .doOnSuccess(v -> log.info("✅ [BROADCAST] Broadcast message type={} to event={} completed",
                        message.get("type"), eventId))
                .onErrorResume(e -> {
                    log.warn("⚠️ [BROADCAST] Error broadcasting to event={}: {}", eventId, e.getMessage());
                    return Mono.empty();
                });
    }
}
