package com.rybki.spring_boot.client;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rybki.spring_boot.model.domain.Idea;
import com.rybki.spring_boot.model.dto.GigaChatRequestDto;
import com.rybki.spring_boot.model.dto.GigaChatResponseDto;
import com.rybki.spring_boot.model.dto.NnResponseDto;
import com.rybki.spring_boot.service.GigaChatAuthService;
import com.rybki.spring_boot.util.LoggerFactoryService;
import com.rybki.spring_boot.util.LoggerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class IdeaExtractorClient {

    private static final LoggerService log = LoggerFactoryService.getLogger(IdeaExtractorClient.class);

    private final WebClient webClient;
    private final GigaChatAuthService authService;
    private final ObjectMapper objectMapper;

    @Value("${gigachat.api.url}")
    private String apiUrl;

    public IdeaExtractorClient(GigaChatAuthService authService) {
        this.webClient = WebClient.builder().build();
        this.objectMapper = new ObjectMapper();
        this.authService = authService;
    }

    public List<Idea> extractIdeas(String text) {
        try {
            String accessToken = authService.getAccessToken();
            if (accessToken == null) {
                return Collections.emptyList();
            }

            GigaChatRequestDto request = GigaChatRequestDto.createIdeaExtractionRequest(text);

            GigaChatResponseDto response = webClient.post()
                .uri(apiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GigaChatResponseDto.class)
                .timeout(Duration.ofSeconds(30))
                .block();

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return Collections.emptyList();
            }

            String content = response.choices().get(0).message().content();

            String jsonContent = extractJsonFromMarkdown(content);

            NnResponseDto nnResponse = objectMapper.readValue(jsonContent, NnResponseDto.class);

            if ("no_ideas_found".equalsIgnoreCase(nnResponse.status())) {
                return Collections.emptyList();
            }

            if (nnResponse.ideas() == null || nnResponse.ideas().isEmpty()) {
                log.warn("nnResponse status is success but no ideas found");
                return Collections.emptyList();
            }

            return nnResponse.ideas().stream()
                .map(idea -> new Idea(
                    idea.id(),
                    idea.title(),
                    idea.description()
                ))
                .toList();

        } catch (JsonProcessingException e) {
            log.error("Failed to process JSON", e);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to extract ideas", e);
            return Collections.emptyList();
        }
    }

    private String extractJsonFromMarkdown(String content) {
        if (content.contains("```json")) {
            int start = content.indexOf("```json") + 7;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        if (content.contains("```")) {
            int start = content.indexOf("```") + 3;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        return content.trim();
    }
}
