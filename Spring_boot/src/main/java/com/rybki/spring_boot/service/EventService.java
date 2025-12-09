package com.rybki.spring_boot.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

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

@Service
@Slf4j
@RequiredArgsConstructor
public class EventService {

    private final RedisEventRepository eventRepository;

    public CreateEventResponse createEvent(final CreateEventRequest eventRequest) {
        final String conferenceId = UUID.randomUUID().toString();
        final String eventId = UUID.randomUUID().toString();

        log.info("Creating new event: eventId={}, conferenceId={}", eventId, conferenceId);

        final Event event = Event.builder()
                .eventId(eventId)
                .creatorClientId(conferenceId)
                .status(EventStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        eventRepository.createEvent(event);

        eventRepository.addParticipant(eventId, conferenceId);

        log.info("Event created successfully: eventId={}", eventId);

        return CreateEventResponse.builder()
                .conferenceId(conferenceId)
                .eventName(eventRequest.getEventName())
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

        String conferenceId = UUID.randomUUID().toString();

        while (eventRepository.isParticipant(eventId, conferenceId)) {
            conferenceId = UUID.randomUUID().toString();
        }

        eventRepository.addParticipant(eventId, conferenceId);

        log.info("Client {} successfully joined event {}", conferenceId, eventId);

        return JoinEventResponse.builder()
                .conferenceId(conferenceId)
                .conferenceName(joinEventRequest.getConferenceName())
                .eventName("") // TODO: получить из Event если добавить поле
                .eventId(eventId)
                .build();
    }

    public EndEventResponse endEvent(final String eventId, final EndEventRequest endEventRequest) {
        log.info("Ending event: eventId={}", eventId);

        // 1. ПРОВЕРЯЕМ СУЩЕСТВУЕТ ЛИ СОБЫТИЕ
        final Event event = eventRepository.findEventById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + eventId));

        // 2. ПРОВЕРЯЕМ ПРАВА (только создатель может завершить)
        if (!event.getCreatorClientId().equals(endEventRequest.getConferenceId())) {
            throw new RuntimeException("Only event creator can end the event");
        }

        eventRepository.deleteEvent(eventId);
        // 4. СОБИРАЕМ СТАТИСТИКУ ИЗ REDIS
        final int participantCount = eventRepository.getParticipants(eventId).size();

        log.info("Event ended successfully: eventId={}, participants={}", eventId, participantCount);

        return new EndEventResponse();
    }
}
