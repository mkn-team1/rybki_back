package com.rybki.spring_boot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttResponseHandler {

    private final IdeaService ideaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void handle(final String json) {
        try {
            final JsonNode node = objectMapper.readTree(json);
            final String type = node.path("type").asText();

            if ("final_text".equals(type)) {
                final String clientId = node.path("clientId").asText();
                final String eventId = node.path("eventId").asText();
                final String text = node.path("text").asText();

                log.info("Received final_text from STT: clientId={}, eventId={}, text={}",
                    clientId, eventId, text);

                ideaService.processText(clientId, eventId, text).subscribe();

            } else {
                log.debug("Unknown STT message type: {}", type);
            }

        } catch (Exception e) {
            log.error("Failed to handle STT message: {}", json, e);
        }
    }
}
