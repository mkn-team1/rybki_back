package com.rybki.spring_boot.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.rybki.spring_boot.exception.BadRequestException;
import com.rybki.spring_boot.exception.NotFoundException;
import com.rybki.spring_boot.model.domain.api.event.create.CreateEventRequest;
import com.rybki.spring_boot.model.domain.api.event.create.CreateEventResponse;
import com.rybki.spring_boot.model.domain.api.event.end.EndEventRequest;
import com.rybki.spring_boot.model.domain.api.event.end.EndEventResponse;
import com.rybki.spring_boot.model.domain.api.event.join.JoinEventRequest;
import com.rybki.spring_boot.model.domain.api.event.join.JoinEventResponse;
import com.rybki.spring_boot.model.domain.api.event.summarize.SummarizeEventRequest;
import com.rybki.spring_boot.model.domain.api.event.summarize.SummarizeEventResponse;
import com.rybki.spring_boot.model.domain.redis.Event;
import com.rybki.spring_boot.model.domain.redis.EventStatus;
import com.rybki.spring_boot.repository.RedisEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventService {

    private final RedisEventRepository eventRepository;
    private final SummaryService summaryService;

    public CreateEventResponse createEvent(final CreateEventRequest eventRequest) {
        final String conferenceId = UUID.randomUUID().toString();
        final String eventId = UUID.randomUUID().toString();

        log.info("Creating new event: eventId={}, conferenceId={}", eventId, conferenceId);

        final Event event = Event.builder()
                .eventId(eventId)
                .creatorConferenceId(conferenceId)
                .eventName(eventRequest.getEventName())
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
                .orElseThrow(() -> new NotFoundException("Event not found with id: " + eventId));

        if (event.getStatus() == EventStatus.ENDED) {
            throw new BadRequestException("Cannot join ended event: " + eventId);
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

        final Event event = eventRepository.findEventById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found with id: " + eventId));

        if (!event.getCreatorConferenceId().equals(endEventRequest.getConferenceId())) {
            throw new BadRequestException("Only event creator can end the event");

        }

        // TODO: реализовать завершение ивента

        eventRepository.deleteEvent(eventId);

        // 4. СОБИРАЕМ СТАТИСТИКУ ИЗ REDIS
        final int participantCount = eventRepository.getParticipants(eventId).size();

        log.info("Event ended successfully: eventId={}, participants={}", eventId, participantCount);

        // 5. TODO: УВЕДОМИТЬ УЧАСТНИКОВ ЧЕРЕЗ WEBSOCKET
        // websocketService.broadcastEventEnded(eventId, summary);

        return EndEventResponse.builder().build();
    }

    // public LeaveEventResponse leaveEvent(final String eventId, final LeaveEventRequest leaveEventRequest) {
    //     final String conferenceId = leaveEventRequest.getConferenceId();
    //     log.info("Client {} leaving event {}", conferenceId, eventId);
    //     final Event event = eventRepository.findEventById(eventId)
    //             .orElseThrow(() -> new NotFoundException("Event not found with id: " + eventId));
    //     if (!eventRepository.isParticipant(eventId, conferenceId)) {
    //         throw new BadRequestException("Client is not a participant of the event: " + eventId);
    //     }
    //     // TODO: Сделать логику при выходе из конференции с выдачей summary
    //     botService.handleClientLeave(eventId, conferenceId);
    //     // eventRepository.removeParticipant(eventId, conferenceId);
    //     log.info("Client {} successfully left event {}", conferenceId, eventId);
    //     return LeaveEventResponse.builder().build();
    // }

    public Mono<SummarizeEventResponse> summarizeEvent(final String eventId,
            final SummarizeEventRequest summarizeEventRequest) {
        log.info("📋 Summarizing event: eventId={}", eventId);

        return summaryService.generateSummary(
                eventId,
                summarizeEventRequest.getMode(),
                summarizeEventRequest.getStyle()
        )
        .map(summary -> SummarizeEventResponse.builder()
                .summaryText(summary)
                .build()
        )
        .doOnSuccess(r -> log.info("✅ Summary prepared for eventId={}", eventId))
        .doOnError(e -> log.error("❌ Failed to generate summary for eventId={}", eventId, e));
    }
}
