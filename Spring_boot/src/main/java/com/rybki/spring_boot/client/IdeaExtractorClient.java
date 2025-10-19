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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdeaExtractorClient {

    private final WebClient webClient = WebClient.builder().build();
    private final GigaChatAuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gigachat.api.url}")
    private String apiUrl;

    public Mono<List<Idea>> extractIdeas(String text) {
        GigaChatRequestDto request = GigaChatRequestDto.createIdeaExtractionRequest(text);

        return authService.getAccessToken()
            .flatMap(accessToken -> {
                if (accessToken == null || accessToken.isEmpty()) {
                    log.warn("No access token available");
                    return Mono.just(Collections.emptyList());
                }

                return webClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(GigaChatResponseDto.class)
                    .timeout(Duration.ofSeconds(30))
                    .flatMap(this::parseResponse)
                    .onErrorResume(e -> {
                        log.error("Failed to extract ideas", e);
                        return Mono.just(Collections.emptyList());
                    });
            });
    }

    private Mono<List<Idea>> parseResponse(GigaChatResponseDto response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        String content = response.choices().get(0).message().content();
        String jsonContent = extractJsonFromMarkdown(content);

        try {
            NnResponseDto nnResponse = objectMapper.readValue(jsonContent, NnResponseDto.class);

            if ("no_ideas_found".equalsIgnoreCase(nnResponse.status())) {
                return Mono.just(Collections.emptyList());
            }

            if (nnResponse.ideas() == null || nnResponse.ideas().isEmpty()) {
                log.warn("nnResponse status is success but no ideas found");
                return Mono.just(Collections.emptyList());
            }

            List<Idea> ideas = nnResponse.ideas().stream()
                .map(idea -> new Idea(
                    idea.id(),
                    idea.title(),
                    idea.description()
                ))
                .toList();

            return Mono.just(ideas);

        } catch (JsonProcessingException e) {
            log.error("Failed to process JSON", e);
            return Mono.just(Collections.emptyList());
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
