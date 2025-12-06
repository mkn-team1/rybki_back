package com.rybki.spring_boot.service;

import java.time.Instant;
import java.util.UUID;

import com.rybki.spring_boot.exception.BadRequestException;
import com.rybki.spring_boot.exception.NotFoundException;
import com.rybki.spring_boot.model.domain.api.event.create.CreateEventRequest;
import com.rybki.spring_boot.model.domain.api.event.create.CreateEventResponse;
import com.rybki.spring_boot.model.domain.api.event.end.EndEventRequest;
import com.rybki.spring_boot.model.domain.api.event.end.EndEventResponse;
import com.rybki.spring_boot.model.domain.api.event.join.JoinEventRequest;
import com.rybki.spring_boot.model.domain.api.event.join.JoinEventResponse;
import com.rybki.spring_boot.model.domain.api.event.leave.LeaveEventRequest;
import com.rybki.spring_boot.model.domain.api.event.leave.LeaveEventResponse;
import com.rybki.spring_boot.model.domain.api.event.summarize.SummarizeEventRequest;
import com.rybki.spring_boot.model.domain.api.event.summarize.SummarizeEventResponse;
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
    private final BotService botService;

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
            .orElseThrow(() -> new NotFoundException("Event not found with id: " + eventId));

        if (event.getStatus() == EventStatus.ENDED) {
            throw new BadRequestException("Cannot join ended event: " + eventId);
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

        final Event event = eventRepository.findEventById(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found with id: " + eventId));

        if (!event.getCreatorClientId().equals(endEventRequest.getClientId())) {
            throw new BadRequestException("Only event creator can end the event");
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

        return EndEventResponse.builder().build();
    }

    public LeaveEventResponse leaveEvent(final String eventId, final LeaveEventRequest leaveEventRequest) {
        final String clientId = leaveEventRequest.getClientId();

        log.info("Client {} leaving event {}", clientId, eventId);

        final Event event = eventRepository.findEventById(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found with id: " + eventId));

        if (!eventRepository.isParticipant(eventId, clientId)) {
            throw new BadRequestException("Client is not a participant of the event: " + eventId);
        }

        // TODO: Сделать логику при выходе из конференции с выдачей summary

        botService.handleClientLeave(eventId, clientId);
        // eventRepository.removeParticipant(eventId, clientId);

        log.info("Client {} successfully left event {}", clientId, eventId);

        return LeaveEventResponse.builder().build();
    }

    public SummarizeEventResponse summarizeEvent(final String eventId,
        final SummarizeEventRequest summarizeEventRequest) {
        log.info("Summarizing event: eventId={}", eventId);

        final Event event = eventRepository.findEventById(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found with id: " + eventId));
        
        // TODO: Собрать и вернуть сводку события

        return SummarizeEventResponse.builder().build();
    }
}
