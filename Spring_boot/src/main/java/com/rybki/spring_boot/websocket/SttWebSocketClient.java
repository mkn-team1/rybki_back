package com.rybki.spring_boot.websocket;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import com.rybki.spring_boot.service.SttResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;


@Slf4j
@RequiredArgsConstructor
public class SttWebSocketClient {

    private final String sttUrl;
    private final Duration timeout;
    private final Duration reconnectInitialDelay;
    private final Duration reconnectMaxDelay;
    private final SttResponseHandler responseHandler;

    private final ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
    private final AtomicLong currentBackoffMs = new AtomicLong();
    private final Sinks.Many<String> outQueue = Sinks.many().unicast().onBackpressureBuffer();

    private volatile WebSocketSession session;
    private volatile boolean running;

    /**
     * Запуск клиента
     */
    public void start() {
        if (running) {
            return;
        }

        running = true;
        currentBackoffMs.set(reconnectInitialDelay.toMillis());
        connect();
        startSenderLoop();
    }

    /**
     * Остановка клиента
     */
    public void stop() {
        running = false;
        final WebSocketSession s = session;
        if (s != null && s.isOpen()) {
            s.close()
                .doOnError(e -> log.error("Error closing STT session", e))
                .subscribe();
        }
    }

    /**
     * Отправка JSON в STT
     */
    public void sendToStt(final String json) {
        final Sinks.EmitResult result = outQueue.tryEmitNext(json);
        if (result.isFailure()) {
            log.warn("Failed to enqueue STT message: {}", result);
        }
    }

    /**
     * Подключение к STT с reconnect/backoff
     */
    private void connect() {
        if (!running) {
            return;
        }

        log.info("Connecting to STT at {}", sttUrl);

        client.execute(URI.create(sttUrl), ws -> {
                this.session = ws;
                currentBackoffMs.set(reconnectInitialDelay.toMillis());
                log.info("Connected to STT server: {}", sttUrl);

                // Запуск приёма сообщений
                return startReceiveLoop(ws)
                    .doFinally(sig -> {
                        log.warn("STT connection closed ({}) — scheduling reconnect", sig);
                        this.session = null;
                        scheduleReconnect();
                    });
            }
        ).doOnError(e -> {
                log.error("Failed to connect to STT, scheduling reconnect", e);
                scheduleReconnect();
            }
        ).subscribe();
    }

    /**
     * Экспоненциальный backoff reconnect
     */
    private void scheduleReconnect() {
        if (!running) {
            return;
        }

        final long delay = currentBackoffMs.get();
        log.info("Reconnecting to STT in {} ms", delay);

        Mono.delay(Duration.ofMillis(delay), Schedulers.boundedElastic())
            .then(Mono.fromRunnable(this::connect))
            .subscribe();

        currentBackoffMs.updateAndGet(prev -> Math.min(prev * 2, reconnectMaxDelay.toMillis()));
    }

    /**
     * Получение сообщений от STT
     */
    private Mono<Void> startReceiveLoop(final WebSocketSession ws) {
        return ws.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .doOnNext(msg -> {
                log.debug("Received from STT: {}", msg);
                try {
                    responseHandler.handle(msg);
                } catch (final Exception e) {
                    log.error("Error while handling STT message", e);
                }
            })
            .onErrorContinue((err, obj) ->
                log.error("Error receiving message from STT", err))
            .then();
    }

    /**
     * Отправка сообщений из очереди
     */
    private void startSenderLoop() {
        outQueue.asFlux()
            .flatMap(msg -> {
                final WebSocketSession s = session;
                if (s != null && s.isOpen()) {
                    return s.send(Mono.just(s.textMessage(msg)))
                        .timeout(timeout)
                        .onErrorResume(e -> {
                            log.warn("Failed to send message to STT", e);
                            return Mono.empty();
                        });
                } else {
                    log.warn("STT session not ready — message dropped");
                    return Mono.empty();
                }
            }, 1) // concurrency = 1 → порядок сохранён
            .subscribe();
    }
}
