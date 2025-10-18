package com.rybki.spring_boot.service;

import java.util.List;

import com.rybki.spring_boot.client.IdeaExtractorClient;
import com.rybki.spring_boot.model.domain.Idea;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdeaService {

    private final IdeaExtractorClient ideaExtractorClient;
    private final OutMessageSenderService outMessageSenderService;

    public void processText(String clientId, String eventId, String text) {
        try {
            List<Idea> ideas = ideaExtractorClient.extractIdeas(text);
            if (ideas == null || ideas.isEmpty()) {
                return;
            }

            for (Idea idea : ideas) {
                outMessageSenderService.sendIdeaToClient(clientId, eventId, idea);
            }

        } catch (Exception e) {
            log.error("Failed to process text for clientId={}, eventId={}", clientId, eventId, e);
        }
    }
}
