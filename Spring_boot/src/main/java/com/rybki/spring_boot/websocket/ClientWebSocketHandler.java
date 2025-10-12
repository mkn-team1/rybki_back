package com.rybki.spring_boot.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.WebSocketMessage;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ClientWebSocketHandler implements WebSocketHandler {

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("New WS connection: sessionId={}", session.getId());

        return session.receive()
            .flatMap(msg -> {
                if (msg.getType() == WebSocketMessage.Type.TEXT) {
                    log.info("Received text message: {}", msg.getPayloadAsText());
                } else if (msg.getType() == WebSocketMessage.Type.BINARY) {
                    log.debug("Received binary message, size={}", msg.getPayload().asByteBuffer().remaining());
                }
                return Mono.empty();
            })
            .doFinally(signal -> log.info("WS closed: sessionId={}", session.getId()))
            .then();
    }
}
