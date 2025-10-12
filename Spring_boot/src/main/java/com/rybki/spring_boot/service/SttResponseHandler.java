package com.rybki.spring_boot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SttResponseHandler {

    private final IdeaService ideaService;
    private final ObjectMapper objectMapper;

    public SttResponseHandler(IdeaService ideaService) {
        this.ideaService = ideaService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Обрабатываем сообщение от STT
     */
    public void handle(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String type = node.path("type").asText();

            if ("final_text".equals(type)) {
                String clientId = node.path("clientId").asText();
                String eventId = node.path("eventId").asText();
                String text = node.path("text").asText();

                log.info("Received final_text from STT: clientId={}, eventId={}, text={}",
                    clientId, eventId, text);

                // делегируем нейронке в ideaService, передавая нужные id и текст

            } else {
                log.debug("Unknown STT message type: {}", type);
            }

        } catch (Exception e) {
            log.error("Failed to handle STT message: {}", json, e);
        }
    }
}
