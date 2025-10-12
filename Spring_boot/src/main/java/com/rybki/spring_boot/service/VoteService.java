package com.rybki.spring_boot.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class VoteService {

    // Заглушка для регистрации голоса
    public Mono<Void> registerVote(String clientId, String eventId, JsonNode voteData) {
        return Mono.fromRunnable(() ->
            log.info("Vote registered: clientId={}, eventId={}, data={}", clientId, eventId, voteData.toString())
        );
    }
}