package com.rybki.spring_boot.controller;

import com.rybki.spring_boot.model.domain.CreateEventRequest;
import com.rybki.spring_boot.model.domain.CreateEventResponse;
import com.rybki.spring_boot.model.domain.EndEventRequest;
import com.rybki.spring_boot.model.domain.EndEventResponse;
import com.rybki.spring_boot.model.domain.JoinEventRequest;
import com.rybki.spring_boot.model.domain.JoinEventResponse;
import com.rybki.spring_boot.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Tag(name = "Event API", description = "Operations with events")
public class EventController {

    private final EventService eventService;

    // Создать событие
    @PostMapping
    @Operation(summary = "Create Event", description = "Creates new event. Return UserID")
    @ApiResponse(responseCode = "200", description = "Event Created")
    @ApiResponse(responseCode = "400", description = "Bad Request - validation error")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    public ResponseEntity<CreateEventResponse> createEvent(
        final @RequestBody @Valid CreateEventRequest eventRequest) {
        final CreateEventResponse response =
            eventService.createEvent(eventRequest);
        return ResponseEntity.ok(response);
    }

    // Присоединиться к событию
    @PostMapping("/{eventId}/join")
    @Operation(summary = "Join Event", description = "Join new event. Return UserID")
    @ApiResponse(responseCode = "200", description = "Joined event")
    @ApiResponse(responseCode = "400", description = "Bad Request - validation error")
    @ApiResponse(responseCode = "404", description = "Event not found")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    public ResponseEntity<JoinEventResponse> joinEvent(final @PathVariable String eventId,
        final @RequestBody @Valid JoinEventRequest joinEventRequest) {
        final JoinEventResponse response = eventService.joinEvent(eventId, joinEventRequest);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/{eventId}/end")
    @Operation(summary = "End Event", description = "End Event")
    @ApiResponse(responseCode = "200", description = "Event ended")
    @ApiResponse(responseCode = "400", description = "Bad Request - validation error")
    @ApiResponse(responseCode = "404", description = "Event not found")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    public ResponseEntity<EndEventResponse> endEvent(final @PathVariable String eventId,
        final @RequestBody @Valid EndEventRequest endEventRequest) {
        final EndEventResponse response =
            eventService.endEvent(eventId, endEventRequest);
        return ResponseEntity.ok(response);
    }
}
