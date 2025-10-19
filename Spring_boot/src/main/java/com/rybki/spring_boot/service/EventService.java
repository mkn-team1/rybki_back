package com.rybki.spring_boot.service;

import java.time.Instant;
import java.util.UUID;

import com.rybki.spring_boot.model.domain.CreateEventRequest;
import com.rybki.spring_boot.model.domain.CreateEventResponse;
import com.rybki.spring_boot.model.domain.EndEventRequest;
import com.rybki.spring_boot.model.domain.EndEventResponse;
import com.rybki.spring_boot.model.domain.JoinEventRequest;
import com.rybki.spring_boot.model.domain.JoinEventResponse;
import com.rybki.spring_boot.model.domain.redis.Event;
import com.rybki.spring_boot.model.domain.redis.EventStatus;
import com.rybki.spring_boot.repository.RedisEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventService {

    private final RedisEventRepository eventRepository;

    public CreateEventResponse createEvent(final CreateEventRequest eventRequest) {
        final String clientId = UUID.randomUUID().toString();
        final String eventId = UUID.randomUUID().toString();

        log.info("Creating new event: eventId={}, creatorId={}", eventId, clientId);

        final Event event = Event.builder()
            .eventId(eventId)
            .creatorClientId(clientId)
            .status(EventStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        eventRepository.createEvent(event);

        eventRepository.addParticipant(eventId, clientId);

        log.info("Event created successfully: eventId={}", eventId);

        return CreateEventResponse.builder()
            .clientId(clientId)
            .eventId(eventId)
            .build();
    }

    public JoinEventResponse joinEvent(final String eventId, final JoinEventRequest joinEventRequest) {
        log.info("Trying to join {} event with extra data {}", eventId, joinEventRequest);

        final Event event = eventRepository.findEventById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found with id: " + eventId));

        if (event.getStatus() == EventStatus.ENDED) {
            throw new RuntimeException("Cannot join ended event: " + eventId);
        }

        String clientId = UUID.randomUUID().toString();

        while (eventRepository.isParticipant(eventId, clientId)) {
            clientId = UUID.randomUUID().toString();
        }

        eventRepository.addParticipant(eventId, clientId);

        log.info("Client {} successfully joined event {}", clientId, eventId);

        return JoinEventResponse.builder()
            .eventId(eventId)
            .clientId(clientId)
            .build();
    }

    public EndEventResponse endEvent(final String eventId, final EndEventRequest endEventRequest) {
        log.info("Ending event: eventId={}", eventId);

        // 1. ПРОВЕРЯЕМ СУЩЕСТВУЕТ ЛИ СОБЫТИЕ
        final Event event = eventRepository.findEventById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found with id: " + eventId));

        // 2. ПРОВЕРЯЕМ ПРАВА (только создатель может завершить)
        if (!event.getCreatorClientId().equals(endEventRequest.getClientId())) {
            throw new RuntimeException("Only event creator can end the event");
        }

        // 3. ОБНОВЛЯЕМ СТАТУС СОБЫТИЯ В REDIS
        /* Если мы хотим именно обновлять статус
        event.setStatus(EventStatus.ENDED);
        event.setEndedAt(Instant.now());
        eventRepository.updateEvent(event);
        */

        eventRepository.deleteEvent(eventId);
        // 4. СОБИРАЕМ СТАТИСТИКУ ИЗ REDIS
        final int participantCount = eventRepository.getParticipants(eventId).size();
        // TODO: собрать информацию об идеях, голосованиях и т.д.

        log.info("Event ended successfully: eventId={}, participants={}", eventId, participantCount);

        // 5. TODO: УВЕДОМИТЬ УЧАСТНИКОВ ЧЕРЕЗ WEBSOCKET
        // websocketService.broadcastEventEnded(eventId, summary);

        return new EndEventResponse(); // можно добавить поля при необходимости
    }
}
