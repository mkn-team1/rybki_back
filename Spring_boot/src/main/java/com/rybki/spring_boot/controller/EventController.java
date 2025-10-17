package com.rybki.spring_boot.controller;

import com.rybki.spring_boot.model.domain.CreateEventRequest;
import com.rybki.spring_boot.model.domain.CreateEventResponse;
import com.rybki.spring_boot.model.domain.EndEventRequest;
import com.rybki.spring_boot.model.domain.EndEventResponse;
import com.rybki.spring_boot.model.domain.JoinEventRequest;
import com.rybki.spring_boot.model.domain.JoinEventResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
public class EventController {

    @Autowired
    private RedisRepository redisRepository;

    // Создать событие
    @PostMapping
    public ResponseEntity<CreateEventResponse> createEvent(
        final @RequestBody @Valid CreateEventRequest eventRequest) {
        final CreateEventResponse response =
            eventService.createEvent(eventRequest);
        return ResponseEntity.ok(response);

    }

    // Присоединиться к событию
    @PostMapping("/{eventId}/join")
    public ResponseEntity<JoinEventResponse> joinEvent(final @PathVariable String eventId,
        final @RequestBody @Valid JoinEventRequest joinEventRequest) {
        final JoinEventResponse response = redisRepository.addParticipantToEvent(eventId, joinEventRequest);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/{eventId}/end")
    public ResponseEntity<EndEventResponse> endEvent(final @PathVariable String eventId,
        final @RequestBody @Valid EndEventRequest endEventRequest) {
        final EndEventResponse response =
            eventService.endEvent(eventId, endEventRequest);
        return ResponseEntity.ok(response);
    }
}