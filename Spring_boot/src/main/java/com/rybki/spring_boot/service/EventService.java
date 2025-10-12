package com.rybki.spring_boot.service;

import com.rybki.spring_boot.model.domain.CreateEventRequest;
import com.rybki.spring_boot.model.domain.EndEventRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EventService {

    public String createEvent(final CreateEventRequest eventRequest) {
        final String eventId = java.util.UUID.randomUUID().toString();
        log.info("Creating new event: eventId={}, creatorId={}", eventId, eventRequest.getClientId());

        // TODO: записать в Redis что то (если это надо делать здесь)

        return eventId;
    }

    public void joinEvent(String eventId, String clientId) {
        log.info("Client {} joined event {}", clientId, eventId);

        // TODO: записать в Redis или список участников, если нужно
    }

    public void endEvent(final EndEventRequest endEventRequest) {
        log.info("Ending event: eventId={}", endEventRequest.getEventId());

        // TODO: собрать все идеи, удалить ключи Redis (?), уведомить участников
    }
}
