package com.rybki.spring_boot.model.domain;

import org.springframework.web.reactive.socket.WebSocketSession;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClientSession {
    private String conferenceId;
    private String conferenceName;
    private String eventId;
    private WebSocketSession session;
}
