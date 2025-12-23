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

    private final SessionService sessionService; 
    private final IdeaService ideaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void handle(final String json) {
        log.debug("🔍 [STT-HANDLER] Message raw JSON: {}", json);
        try {
            final JsonNode node = objectMapper.readTree(json);
            final String type = node.path("type").asText();

            if ("final_text".equals(type)) {
                final String conferenceId = node.path("conferenceId").asText();
                final String text = node.path("text").asText();

                log.info("📤 [STT] Received final_text from STT: conferenceId={}, text: {}",
                        conferenceId, text);

                sessionService.getConferenceInfo(conferenceId)
                    .flatMap(info -> ideaService.processText(info.getConferenceId(), info.getConferenceName(), info.getEventId(), text)).subscribe();
                
            } else {
                log.debug("Unknown STT message type: {}", type);
            }

        } catch (Exception e) {
            log.error("❌ [STT-HANDLER] Failed to parse STT response", e);
        }
    }
}
