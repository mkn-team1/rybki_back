package com.rybki.spring_boot.model.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.reactive.socket.WebSocketSession;

@Getter
@Setter
public class ClientSession {
    private final String clientId;
    private final String eventId;
    private final WebSocketSession session;

    public ClientSession(String clientId, String eventId, WebSocketSession session) {
        this.clientId = clientId;
        this.eventId = eventId;
        this.session = session;
    }
}
