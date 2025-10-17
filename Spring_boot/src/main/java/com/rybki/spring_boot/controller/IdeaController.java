package com.rybki.spring_boot.controller;

import com.rybki.spring_boot.model.domain.AcceptIdeaRequest;
import com.rybki.spring_boot.model.domain.VoteRequest;
import com.rybki.spring_boot.model.domain.VoteResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ideas")
public class IdeaController {

    @PostMapping("/{ideaId}/vote")
    public ResponseEntity<VoteResponse> voteForIdea(
        final @PathVariable String ideaId,
        final @RequestBody @Valid VoteRequest voteRequest) {
        // Голосование за идею
    }

    @PostMapping("/{ideaId}/accept")
    public ResponseEntity<AcceptIdeaResponse> acceptIdea(
        final @PathVariable String ideaId,
        final @RequestBody @Valid AcceptIdeaRequest acceptRequest) {
        // Подтверждение идеи (альтернатива голосованию)
    }
}