package com.rybki.spring_boot.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
     * Broadcast a new idea to all connected clients for an event
     */
    public Mono<Void> broadcastIdea(final String conferenceId, final String eventId, final Idea idea) {
        return broadcastToEvent(eventId, Map.of(
                "type", "idea",
                "conferenceId", conferenceId,
                "eventId", eventId,
                "idea", idea));
    }

    /**
     * Broadcast idea deletion to all connected clients for an event
     */
    public Mono<Void> broadcastIdeaDeleted(final String conferenceId, final String eventId, final String ideaId) {
        return broadcastToEvent(eventId, Map.of(
                "type", "idea_deleted",
                "conferenceId", conferenceId,
                "eventId", eventId,
                "ideaId", ideaId));
    }

    /**
     * Broadcast idea reaction update to all connected clients for an event
     */
    public Mono<Void> broadcastIdeaReaction(final String conferenceId, final String eventId, final String ideaId,
            final String reactionType, final int likes, final int dislikes) {
        return broadcastToEvent(eventId, Map.of(
                "type", "idea_reaction",
                "conferenceId", conferenceId,
                "eventId", eventId,
                "ideaId", ideaId,
                "reaction", reactionType,
                "likes", likes,
                "dislikes", dislikes));
    }

    /**
     * Broadcast list of ideas to all connected clients for an event
     */
    public Mono<Void> broadcastIdeasList(final String conferenceId, final String eventId, final List<Idea> ideas) {
        return broadcastToEvent(eventId, Map.of(
                "type", "ideas_list",
                "conferenceId", conferenceId,
                "eventId", eventId,
                "ideas", ideas));
    }

    /**
     * Send bot connection notification to a specific client
     */
    public Mono<Void> botConnected(final String conferenceId, final String botId) {
        return sendToClient(conferenceId, Map.of(
                "type", "bot_connected",
                "botId", botId));
    }

    /**
     * Send bot disconnection notification to a specific client
     */
    public Mono<Void> botDisconnected(final String conferenceId, final String botId) {
        return sendToClient(conferenceId, Map.of(
                "type", "bot_disconnected",
                "botId", botId));
    }

    /**
     * Send a message to a specific client by conference ID
     */
    public Mono<Void> sendToClient(final String conferenceId, final Map<String, Object> message) {
        return sessionService.getClientSession(conferenceId)
                .flatMap(session -> {
                    try {
                        final String jsonMessage = objectMapper.writeValueAsString(message);
                        @SuppressWarnings("null")
                        Mono<Void> result = session.getSession()
                                .send(Mono.just(session.getSession().textMessage(jsonMessage)))
                                .doOnSuccess(v -> log.info("✅ [NOTIFICATION] Sent message type={} to client "
                                        + "conferenceId={}", message.get("type"), conferenceId))
                                .doOnError(e -> log.error("❌ [NOTIFICATION] Failed to send message to client: {}",
                                        conferenceId, e))
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
     * Broadcast a message to all clients connected to an event
     */
    private Mono<Void> broadcastToEvent(final String eventId, final Map<String, Object> message) {
        log.info("📡 [BROADCAST] Starting broadcast for eventId={}, messageType={}", eventId, message.get("type"));
        return sessionService.getSessionsForEvent(eventId)
                .doOnNext(session -> log.debug("📡 [BROADCAST] Found session for eventId={}: {}", eventId, session.getSession().getId()))
                .flatMap(clientSession -> {
                    try {
                        final String jsonMessage = objectMapper.writeValueAsString(message);
                        final WebSocketSession session = clientSession.getSession();
                        @SuppressWarnings("null")
                        final Mono<Void> result = session.send(
                                Mono.just(session.textMessage(jsonMessage)))
                                .doOnError(e -> log.error(
                                        "❌ [BROADCAST] Failed to broadcast to session: {}",
                                        session.getId(), e));
                        return result;
                    } catch (final IOException e) {
                        log.error("❌ [BROADCAST] Failed to serialize broadcast message", e);
                        return Mono.empty();
                    }
                })
                .collectList()
                .doOnNext(list -> log.info("📡 [BROADCAST] Broadcasting to {} sessions for eventId={}", list.size(), eventId))
                .then()
                .doOnSuccess(v -> log.info("✅ [BROADCAST] Broadcast message type={} to event={} completed",
                        message.get("type"), eventId))
                .onErrorResume(e -> {
                    log.warn("⚠️ [BROADCAST] Error broadcasting to event={}: {}", eventId, e.getMessage());
                    return Mono.empty();
                });
    }
}
