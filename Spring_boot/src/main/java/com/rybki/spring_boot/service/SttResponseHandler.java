package com.rybki.spring_boot.service;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttResponseHandler {

    private final IdeaService ideaService;
    private final SessionService sessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void handle(final String json) {
        log.debug("🔍 [STT-HANDLER] Message raw JSON: {}", json);
        try {
            final JsonNode node = objectMapper.readTree(json);
            final String type = node.path("type").asText();

            if ("final_text".equals(type)) {
                final String conferenceId = node.path("clientId").asText();
                final String eventId = node.path("eventId").asText();
                final String text = node.path("text").asText();

                log.info("📤 [STT] Received final_text from STT: conferenceId={}, eventId={}, text={}",
                        conferenceId, eventId, text);

                
                sessionService.getClientSession(conferenceId)
                        .flatMap(cs -> ideaService.processText(conferenceId, cs.getConferenceName(), eventId, text))
                        .subscribe();
                
            } else {
                log.debug("Unknown STT message type: {}", type);
            }

        } catch (Exception e) {
            log.error("❌ [STT-HANDLER] Failed to parse STT response", e);
        }
    }
}
