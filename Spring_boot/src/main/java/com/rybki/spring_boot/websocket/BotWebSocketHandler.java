package com.rybki.spring_boot.websocket;

import java.nio.ByteBuffer;

import com.rybki.spring_boot.service.SessionService;
import com.rybki.spring_boot.service.SttRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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
@RequiredArgsConstructor
public class BotWebSocketHandler implements WebSocketHandler {

    private final SessionService sessionService;
    private final SttRoutingService sttRoutingService;

    @Override
    public @NotNull Mono<Void> handle(@NotNull WebSocketSession session) {
        final String botId = extractIdFromPath(session.getHandshakeInfo().getUri().getPath());
        if (botId == null) {
            log.warn("Bot session connected without botId, closing");
            return session.close();
        }

        log.info("Bot connected: botId={}, sessionId={}", botId, session.getId());
        sessionService.registerBot(botId, session);

        return session
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
                sessionService.unregisterBot(botId).subscribe();
            })
            .then();
    }

    /** BINARY → пересылаем в STT */
    private Mono<Void> handleBinary(String botId, WebSocketMessage msg) {
        return Mono.fromRunnable(() -> {
            try {
                ByteBuffer buffer = msg.getPayload().asByteBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                // Найти клиентId, связанного с ботом
                String clientId = sessionService.getClientForBot(botId);
                if (clientId == null) {
                    log.debug("Bot {} sent audio but no linked client — ignoring", botId);
                    return;
                }

                // Получить ClientSession по clientId
                sessionService.getClientSession(clientId)
                    .flatMap(cs ->
                        sttRoutingService.forwardAudio(
                            cs.clientId(),   // clientId
                            cs.eventId(),    // eventId
                            bytes            // PCM audio
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
