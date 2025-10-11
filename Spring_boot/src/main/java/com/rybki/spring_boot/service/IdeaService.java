package com.rybki.spring_boot.service;

import java.util.List;

import com.rybki.spring_boot.client.IdeaExtractorClient;
import com.rybki.spring_boot.model.domain.Idea;
import com.rybki.spring_boot.util.LoggerFactoryService;
import com.rybki.spring_boot.util.LoggerService;
import org.springframework.stereotype.Service;

@Service
public class IdeaService {

    private static final LoggerService log = LoggerFactoryService.getLogger(IdeaService.class);

    private final IdeaExtractorClient ideaExtractorClient;
    private final SessionService sessionService;

    public IdeaService(IdeaExtractorClient ideaExtractorClient, SessionService sessionService) {
        this.ideaExtractorClient = ideaExtractorClient;
        this.sessionService = sessionService;
    }

    public void processText(String clientId, String eventId, String text) {
        try {
            List<Idea> ideas = ideaExtractorClient.extractIdeas(text);
            if (ideas == null || ideas.isEmpty()) {
                return;
            }

            for (Idea idea : ideas) {
                // TODO: Waiting for sessionService method
                // sessionService.sendIdeaToClient(clientId, eventId, idea);
            }

        } catch (Exception e) {
            log.error("Failed to process text for clientId={}, eventId={}", clientId, eventId, e);
        }
    }
}
