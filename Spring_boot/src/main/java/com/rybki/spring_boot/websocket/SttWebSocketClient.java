package com.rybki.spring_boot.websocket;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import com.rybki.spring_boot.service.SttResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Slf4j
@RequiredArgsConstructor
public class SttWebSocketClient {

    private final String sttUrl;
    private final Duration reconnectInitialDelay;
    private final SttResponseHandler responseHandler;

    private final ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
    private final AtomicLong currentBackoffMs = new AtomicLong();
    
    private final Sinks.Many<String> outQueue = Sinks.many().unicast().onBackpressureBuffer();

    private volatile boolean running;

    public void start() {
        if (running) return;
        running = true;
        currentBackoffMs.set(reconnectInitialDelay.toMillis());
        connect();
    }

    public void stop() {
        running = false;
        // Закрытие произойдет само при разрыве потока в connect, 
        // но можно добавить явный close() если сохранять Disposable
    }

    public void sendToStt(final String json) {
        try {
            outQueue.emitNext(json, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
        } catch (Sinks.EmissionException e) {
            log.error("❌ [STT-CLIENT] Failed to emit message after retry loop. Reason: {}", e.getReason());            
        } catch (Exception e) {
            log.error("❌ [STT-CLIENT] Unexpected error emitting message", e);
        }
    }

    private void connect() {
        if (!running) return;

        log.info("🔗 [STT-CLIENT] Connecting to {}", sttUrl);

        client.execute(URI.create(sttUrl), session -> {
            log.info("🔗 [STT-CLIENT] Connected id={}", session.getId());
            currentBackoffMs.set(reconnectInitialDelay.toMillis());

            // 1. Поток ВХОДЯЩИХ сообщений
            Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(msg -> {
                    try {
                        responseHandler.handle(msg);
                    } catch (Exception e) {
                        log.error("Error handling STT msg", e);
                    }
                })
                .then();

            // 2. Поток ИСХОДЯЩИХ сообщений
            Mono<Void> output = session.send(
                outQueue.asFlux()
                    .map(session::textMessage)
                    .doOnError(e -> log.error("Error in sender stream", e))
            );

            return Mono.zip(input, output).then();

        })
        .retryWhen(reactor.util.retry.Retry.backoff(Long.MAX_VALUE, reconnectInitialDelay)
            .doBeforeRetry(s -> log.warn("🔄 Reconnecting STT... Attempt {}", s.totalRetries() + 1))
        )
        .subscribe(
            null,
            e -> log.error("❌ Fatal STT Client Error", e),
            () -> log.info("STT Client stopped")
        );
    }
}
