package com.rybki.spring_boot.websocket;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
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

    private final ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
    private final AtomicLong currentBackoffMs = new AtomicLong();
    private final Sinks.Many<String> outQueue = Sinks.many().unicast().onBackpressureBuffer();
    private volatile WebSocketSession session;
    private volatile boolean running;
    @Setter
    private SttMessageHandler messageHandler;

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
                .doOnError(e -> log.error("Error closing session", e))
                .subscribe();
        }
    }

    /**
     * Отправка JSON в STT
     */
    public void sendToStt(final String json) {
        final Sinks.EmitResult result = outQueue.tryEmitNext(json);
        if (result.isFailure()) {
            log.warn("Failed to enqueue message: {}", result);
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

        client.execute(URI.create(sttUrl), webSocketsession -> {
                log.info("Connected to STT");
                this.session = webSocketsession;
                currentBackoffMs.set(reconnectInitialDelay.toMillis());

                final Mono<Void> inbound = session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(msg -> {
                        if (messageHandler != null) {
                            messageHandler.onMessage(msg);
                        }
                    })
                    .then();

                return inbound
                    .doFinally(sig -> {
                        log.warn("STT connection closed, scheduling reconnect");
                        this.session = null;
                        scheduleReconnect();
                    });
            })
            .doOnError(e -> {
                log.error("Failed to connect to STT, scheduling reconnect", e);
                scheduleReconnect();
            })
            .subscribe();
    }

    /**
     * Экспоненциальный backoff reconnect
     */
    private void scheduleReconnect() {
        if (!running) {
            return;
        }

        final long delay = currentBackoffMs.get();
        log.info("Reconnecting in {} ms", delay);

        Mono.delay(Duration.ofMillis(delay), Schedulers.boundedElastic())
            .then(Mono.fromRunnable(this::connect))
            .subscribe();

        // увеличиваем backoff для следующей попытки
        currentBackoffMs.updateAndGet(prev -> Math.min(prev * 2, reconnectMaxDelay.toMillis()));
    }

    /**
     * Фоновый loop для отправки сообщений из очереди
     */
    private void startSenderLoop() {
        outQueue.asFlux()
            .flatMap(msg -> {
                final WebSocketSession s = session;
                if (s != null && s.isOpen()) {
                    return s.send(Mono.just(s.textMessage(msg)))
                        .timeout(timeout)
                        .doOnError(e -> log.warn("Failed to send message to STT", e))
                        .onErrorResume(e -> Mono.empty());
                } else {
                    log.warn("STT session not ready, dropping message");
                    return Mono.empty();
                }
            }, 1) // concurrency = 1, сохраняем порядок
            .subscribe();
    }

    public interface SttMessageHandler {

        void onMessage(String json);
    }
}
