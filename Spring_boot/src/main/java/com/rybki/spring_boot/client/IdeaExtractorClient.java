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
import com.rybki.spring_boot.service.DtoRequestFactoryService;
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
    private final DtoRequestFactoryService dtoRequestFactoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gigachat.api.url}")
    private String apiUrl;
    @Value("${spring.data.ideaExtractorTimeout}")
    private Integer timeoutSeconds;

    public Mono<List<Idea>> extractIdeas(final String text) {
        final GigaChatRequestDto request = dtoRequestFactoryService.createIdeaExtractionRequest(text);

        log.debug("📤 [GIGACHAT] Sending request to find ideas from: {}", text);

        return authService.getAccessToken()
            .flatMap(accessToken -> {
                if (accessToken == null || accessToken.isEmpty()) {
                    log.warn("❌ [GIGACHAT] No access token available");
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
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .doOnNext(response -> {
                        log.debug("📥 [GIGACHAT] Response: {}", response);
                    })
                    .flatMap(this::parseResponse)
                    .doOnSuccess(ideas -> {
                        log.info("✅ [GIGACHAT] Extracted {} ideas", ideas.size());
                        log.debug("📥 [GIGACHAT] Extracted ideas: {}", ideas);

                        if (!ideas.isEmpty()) {
                            final List<String> ideaStrings = extractIdeaStrings(ideas);
                            dtoRequestFactoryService.addFoundIdeas(ideaStrings);
                            log.debug("💾 [GIGACHAT] Saved {} ideas to context", ideaStrings.size());
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("❌ [GIGACHAT] Request failed: {}", e.getMessage(), e);
                        return Mono.just(Collections.emptyList());
                    });
            });
    }


    private List<String> extractIdeaStrings(final List<Idea> ideas) {
        return ideas.stream()
            .map(idea -> {
                final String ideaString = idea.title() + ": " + idea.description();
                log.debug("💡 [GIGACHAT] Converting to string: {}",
                    ideaString);
                return ideaString;
            })
            .toList();
    }

    private Mono<List<Idea>> parseResponse(final GigaChatResponseDto response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        final String content = response.choices().getFirst().message().content();
        final String jsonContent = extractJsonFromMarkdown(content);

        try {
            final NnResponseDto nnResponse = objectMapper.readValue(jsonContent, NnResponseDto.class);

            if ("no_ideas_found".equalsIgnoreCase(nnResponse.status())) {
                return Mono.just(Collections.emptyList());
            }

            if (nnResponse.ideas() == null || nnResponse.ideas().isEmpty()) {
                log.warn("⚠️ [GIGACHAT] nnResponse status is success but no ideas found");
                return Mono.just(Collections.emptyList());
            }

            final List<Idea> ideas = nnResponse.ideas().stream()
                .map(idea -> new Idea(
                    idea.id(),
                    idea.title(),
                    idea.description()
                ))
                .toList();

            return Mono.just(ideas);

        } catch (final JsonProcessingException e) {
            log.error("❌ [GIGACHAT] Failed to process JSON", e);
            return Mono.just(Collections.emptyList());
        }
    }

    private String extractJsonFromMarkdown(final String content) {
        if (content.contains("```json")) {
            final int start = content.indexOf("```json") + "```json".length();
            final int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        if (content.contains("```")) {
            final int start = content.indexOf("```") + "```".length();
            final int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        return content.trim();
    }
}
