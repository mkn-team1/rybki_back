package com.rybki.spring_boot.websocket;

import com.rybki.spring_boot.service.AudioDumpService;
import com.rybki.spring_boot.service.BotService;
import com.rybki.spring_boot.service.SessionService;
import com.rybki.spring_boot.service.SttRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * WS для ботов, /ws/bot/{botId}
 *
 * - регистрирует botId → WebSocketSession
 * - принимает BINARY (PCM16LE) и отправляет в STT
 * - при закрытии — отписывает бота
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotWebSocketHandler implements WebSocketHandler {

    private final SessionService sessionService;
    private final SttRoutingService sttRoutingService;
    private final BotService botService;
    private final AudioDumpService audioDumpService;

    @Override
    public @NotNull Mono<Void> handle(@NotNull WebSocketSession session) {
        final String botId = extractIdFromPath(session.getHandshakeInfo().getUri().getPath());
        if (botId == null) {
            log.warn("Bot session connected without botId, closing");
            return session.close();
        }

        log.info("Bot connected: botId={}, sessionId={}", botId, session.getId());
        return botService.handleBotStarted(session, botId).then(
            session
            .receive()
            .flatMap(msg -> {
                if (msg.getType() == WebSocketMessage.Type.BINARY) {
                    return handleBinary(botId, msg);
                } else if (msg.getType() == WebSocketMessage.Type.TEXT) {
                    return handleText(botId, msg);
                }
                return Mono.empty();
            })
            .doFinally(signal -> {
                log.info("Bot disconnected: botId={}, sessionId={}, signal={}",
                        botId, session.getId(), signal);
            }).then(botService.handleBotRemoved(botId))
        );
    }

    /** BINARY → пересылаем в STT */
    private Mono<Void> handleBinary(String botId, WebSocketMessage msg) {

        if (botService.isMicMuted(botId)) {
            log.debug("Bot {} sent audio but mic is muted — ignoring", botId);
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            try {
                final byte[] bytes = new byte[msg.getPayload().readableByteCount()];
                msg.getPayload().read(bytes);

                // Найти клиентId, связанного с ботом
                String conferenceId = sessionService.getClientForBot(botId);
                if (conferenceId == null) {
                    log.debug("Bot {} sent audio but no linked client — ignoring", botId);
                    return;
                }

                // Получить ClientSession по conferenceId
                sessionService.getClientSession(conferenceId)
                    .flatMap(cs ->
                        sttRoutingService.forwardAudio(
                            cs.getConferenceId(),   // conferenceId
                            cs.getEventId(),        // eventId
                            bytes                   // PCM audio
                        )
                        .then(
                            audioDumpService.append(cs.getSession().getId(), bytes)
                        )
                    )
                    .doOnError(e -> log.error("Failed to forward audio from bot {}", botId, e))
                    .subscribe();

            } catch (Exception e) {
                log.error("Error while handling binary bot message for {}", botId, e);
            }
        });
    }

    // TEXT -> пока что просто логируем
    private Mono<Void> handleText(String botId, WebSocketMessage msg) {
        log.debug("Bot {} text message: {}", botId, msg.getPayloadAsText());
        return Mono.empty();
    }

    private static String extractIdFromPath(String path) {
        if (path == null) return null;
        String[] parts = path.split("/");
        return parts.length == 0 ? null : parts[parts.length - 1];
    }
}
