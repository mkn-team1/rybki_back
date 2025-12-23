package com.rybki.spring_boot.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rybki.spring_boot.model.domain.api.event.create.CreateEventRequest;
import com.rybki.spring_boot.model.domain.api.event.create.CreateEventResponse;
import com.rybki.spring_boot.model.domain.api.event.end.EndEventRequest;
import com.rybki.spring_boot.model.domain.api.event.end.EndEventResponse;
import com.rybki.spring_boot.model.domain.api.event.join.JoinEventRequest;
import com.rybki.spring_boot.model.domain.api.event.join.JoinEventResponse;
import com.rybki.spring_boot.model.domain.api.event.summarize.SummarizeEventRequest;
import com.rybki.spring_boot.model.domain.api.event.summarize.SummarizeEventResponse;
import com.rybki.spring_boot.service.EventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Tag(name = "Event API", description = "Operations with events")
public class EventController {

    private final EventService eventService;

    // Создать событие
    @PostMapping("/create")
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

    // Завершить событие
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

    // Покинуть событие
    // @PostMapping("/{eventId}/leave")
    // @Operation(summary = "Leave Event", description = "Leave Event")
    // @ApiResponse(responseCode = "200", description = "Event left")
    // @ApiResponse(responseCode = "400", description = "Bad Request - validation error")
    // @ApiResponse(responseCode = "404", description = "Event not found")
    // @ApiResponse(responseCode = "500", description = "Internal Server Error")
    // public ResponseEntity<LeaveEventResponse> leaveEvent(final @PathVariable String eventId,
    //     final @RequestBody @Valid LeaveEventRequest leaveEventRequest) {
    //     final LeaveEventResponse response = 
    //         eventService.leaveEvent(eventId, leaveEventRequest);
    //     return ResponseEntity.ok(response);
    // }

    // Получить сводку события
    /*@GetMapping("/{eventId}/summary")
    @Operation(summary = "Summarize Event", description = "Get Event Summary")
    public ResponseEntity<SummarizeEventResponse> summarizeEvent(final @PathVariable String eventId,
        final @RequestBody @Valid SummarizeEventRequest summarizeEventRequest) {
        final SummarizeEventResponse response = eventService.summarizeEvent(eventId, summarizeEventRequest);
        return ResponseEntity.ok(response);
    }*/    
   
   // Получить сводку события
    @PostMapping("/{conferenceId}/summary")
    @Operation(summary = "Summarize Event", description = "Get Event Summary by Conference ID")
    @ApiResponse(responseCode = "200", description = "Event summary retrieved")
    @ApiResponse(responseCode = "400", description = "Bad Request - validation error")
    @ApiResponse(responseCode = "404", description = "Conference or Event not found")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    public Mono<ResponseEntity<SummarizeEventResponse>> summarizeEventByConference(
            final @PathVariable String conferenceId,
            final @RequestBody @Valid SummarizeEventRequest summarizeEventRequest) {

        return eventService.summarizeEventByConference(conferenceId, summarizeEventRequest)
                .map(summary -> ResponseEntity.ok(summary));
    }
}
