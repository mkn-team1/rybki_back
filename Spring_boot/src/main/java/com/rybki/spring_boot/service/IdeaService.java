package com.rybki.spring_boot.service;

import com.rybki.spring_boot.client.IdeaExtractorClient;
import com.rybki.spring_boot.model.domain.Idea;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdeaService {

    private final IdeaExtractorClient ideaExtractorClient;
    private final ClientNotificationService clientNotificationService;

    public Mono<Void> processText(String clientId, String eventId, String text) {
        return ideaExtractorClient.extractIdeas(text)
            .flatMap(ideas -> processIdeas(clientId, eventId, ideas))
            .doOnSuccess(v -> log.info("Completed processing ideas for clientId={}, eventId={}", clientId, eventId))
            .doOnError(e -> log.error("Failed to process text for clientId={}, eventId={}", clientId, eventId, e))
            .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> processIdeas(String clientId, String eventId, List<Idea> ideas) {
        if (ideas == null || ideas.isEmpty()) {
            log.info("No ideas found for clientId={}, eventId={}", clientId, eventId);
            return Mono.empty();
        }

        log.info("Processing {} ideas for clientId={}, eventId={}", ideas.size(), clientId, eventId);

        return Flux.fromIterable(ideas)
            .flatMap(idea -> clientNotificationService.sendIdeaToClient(clientId, eventId, idea))
            .then();
    }
}
