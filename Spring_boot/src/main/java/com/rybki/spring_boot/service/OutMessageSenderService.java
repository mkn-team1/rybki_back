package com.rybki.spring_boot.service;

import com.rybki.spring_boot.model.domain.Idea;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutMessageSenderService {

    private final SessionService sessionService;

    public void sendIdeaToClient(String eventId, String clientId, Idea payload) {
        // TODO: Waiting for sessionService method
//        var session = sessionService.getSession(eventId, clientId);
//        if (session == null) {
//            log.warn("Session (eventId, clientId) = ({}, {}) not found", eventId, clientId);
//            return;
//        }
//        try {
//            String json = new ObjectMapper().writeValueAsString(payload);
//            session.sendMessage(new TextMessage(json));
//        } catch (IOException e) {
//            log.error("Failed to send message to session {}", sessionId, e);
//        }
    }

    // TODO: add other methods to send data out of backend
}
