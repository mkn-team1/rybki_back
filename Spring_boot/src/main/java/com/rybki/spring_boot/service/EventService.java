package com.rybki.spring_boot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EventService {

    public String createEvent(String creatorId) {
        String eventId = java.util.UUID.randomUUID().toString();
        log.info("Creating new event: eventId={}, creatorId={}", eventId, creatorId);

        // TODO: записать в Redis что то (если это надо делать здесь)

        return eventId;
    }

    public void endEvent(String eventId) {
        log.info("Ending event: eventId={}", eventId);

        // TODO: собрать все идеи, удалить ключи Redis (?), уведомить участников
    }
}
