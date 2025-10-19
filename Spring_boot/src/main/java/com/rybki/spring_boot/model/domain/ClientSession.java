package com.rybki.spring_boot.model.domain;

import org.springframework.web.reactive.socket.WebSocketSession;

public record ClientSession(String clientId, String eventId, WebSocketSession session) {}
