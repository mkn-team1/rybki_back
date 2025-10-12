package com.rybki.spring_boot.websocket;

import com.rybki.spring_boot.service.SttResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.WebSocketMessage;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * WS-клиент к STT
 */
@Slf4j
@Component
public class SttWebSocketClient {

    private final String sttUri = "ws://localhost:8081/stt"; // заменить на реальный STT
    private final ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
    private WebSocketSession session;
    private final BlockingQueue<String> outQueue = new LinkedBlockingQueue<>(1000); // bounded queue
    private final SttResponseHandler responseHandler;

    public SttWebSocketClient(SttResponseHandler responseHandler) {
        this.responseHandler = responseHandler;
    }

    /**
     * Запуск после старта Spring
     */
    @PostConstruct
    public void start() {
        new Thread(this::connectLoop, "STT-WS-Client-Thread").start();
    }

    /**
     * Reconnect loop с экспоненциальным backoff
     */
    private void connectLoop() {
        int attempt = 0;
        while (true) {
            try {
                log.info("Connecting to STT at {}", sttUri);
                client.execute(URI.create(sttUri), this::handleSession).block();
            } catch (Exception e) {
                attempt++;
                long backoff = Math.min(30_000, (long)Math.pow(2, attempt) * 1000); // max 30s
                log.warn("STT connection failed, retry in {} ms", backoff);
                try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Handle WS session
     */
    private Mono<Void> handleSession(WebSocketSession wsSession) {
        log.info("Connected to STT, sessionId={}", wsSession.getId());
        this.session = wsSession;

        // background sender
        Mono<Void> sender = Mono.fromRunnable(() -> {
            while (wsSession.isOpen()) {
                try {
                    String msg = outQueue.take();
                    wsSession.send(Mono.just(wsSession.textMessage(msg))).block();
                    log.debug("Sent to STT: {}", msg);
                } catch (InterruptedException e) {
                    log.warn("Sender interrupted");
                    break;
                } catch (Exception e) {
                    log.error("Failed to send message to STT", e);
                }
            }
        }).then();

        // receiver
        Mono<Void> receiver = wsSession.receive()
            .doOnNext(msg -> {
                String payload = msg.getPayloadAsText();
                log.debug("Received from STT: {}", payload);
                responseHandler.handle(payload); // делегируем обработку
            })
            .then();

        return Mono.when(sender, receiver)
            .doFinally(sig -> log.info("STT session closed, sessionId={}", wsSession.getId()));
    }

    /**
     * Поставить JSON-сообщение в очередь для отправки STT
     */
    public void sendToStt(String json) {
        boolean offered = outQueue.offer(json);
        if (!offered) {
            log.warn("STT queue full, dropping message: {}", json);
        }
    }

}
