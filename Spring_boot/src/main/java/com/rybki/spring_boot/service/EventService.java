package com.rybki.spring_boot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EventService {

    public String createEvent(final String creatorId) {
        final String eventId = java.util.UUID.randomUUID().toString();
        log.info("Creating new event: eventId={}, creatorId={}", eventId, creatorId);

        // TODO: записать в Redis что то (если это надо делать здесь)

        return eventId;
    }

    public void endEvent(final String eventId) {
        log.info("Ending event: eventId={}", eventId);

        // TODO: собрать все идеи, удалить ключи Redis (?), уведомить участников
    }
}
