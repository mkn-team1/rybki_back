package com.rybki.spring_boot.client;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rybki.spring_boot.llm.contract.LlmClient;
import com.rybki.spring_boot.llm.contract.LlmRequest;
import com.rybki.spring_boot.llm.contract.LlmResponse;
import com.rybki.spring_boot.llm.gigachat.dto.NnResponseDto;
import com.rybki.spring_boot.model.domain.Idea;
import com.rybki.spring_boot.service.LlmRequestFactoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Клиент для извлечения идей из текста с помощью LLM.
 * Работает с абстрактным LlmClient, что позволяет легко менять провайдера.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdeaExtractorClient {

    private static final String JSON_MARKDOWN = "```json";
    private static final String MARKDOWN_CODE = "```";
    private static final String NO_IDEAS_STATUS = "no_ideas_found";

    private final LlmClient llmClient;
    private final LlmRequestFactoryService requestFactoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Извлечь идеи из текста используя LLM.
     *
     * @param text текст для анализа
     * @return Mono со списком извлеченных идей
     */
    public Mono<List<Idea>> extractIdeas(final String text) {
        log.info("📤 [IDEA_EXTRACTOR] Sending request to {} for idea extraction",
                llmClient.getProviderName());

        final LlmRequest request = requestFactoryService.createIdeaExtractionRequest(text);

        return llmClient.sendRequest(request)
                .flatMap(this::parseResponse)
                .doOnSuccess(ideas -> {
                    log.info("✅ [IDEA_EXTRACTOR] Extracted {} ideas", ideas.size());
                    if (!ideas.isEmpty()) {
                        final List<String> ideaStrings = extractIdeaStrings(ideas);
                        requestFactoryService.addFoundIdeas(ideaStrings);
                        log.debug("💾 [IDEA_EXTRACTOR] Saved {} ideas to context", ideaStrings.size());
                    }
                })
                .onErrorResume(e -> {
                    log.error("❌ [IDEA_EXTRACTOR] Request failed: {}", e.getMessage(), e);
                    return Mono.just(Collections.emptyList());
                });
    }

    /**
     * Извлечь идеи из ответа LLM.
     *
     * @param response ответ от LLM
     * @return Mono со списком идей
     */
    private Mono<List<Idea>> parseResponse(final LlmResponse response) {
        final String content = response.getContent();

        if (content == null || content.isEmpty()) {
            log.warn("⚠️ [IDEA_EXTRACTOR] Empty response content");
            return Mono.just(Collections.emptyList());
        }

        final String jsonContent = extractJsonFromMarkdown(content);
        return parseJsonResponse(jsonContent);
    }

    /**
     * Парсить JSON ответ от LLM.
     *
     * @param jsonContent JSON содержимое
     * @return Mono со списком идей
     */
    private Mono<List<Idea>> parseJsonResponse(final String jsonContent) {
        try {
            final NnResponseDto nnResponse = objectMapper.readValue(jsonContent, NnResponseDto.class);

            if (NO_IDEAS_STATUS.equalsIgnoreCase(nnResponse.status())) {
                log.info("ℹ️ [IDEA_EXTRACTOR] No ideas found in response");
                return Mono.just(Collections.emptyList());
            }

            if (nnResponse.ideas() == null || nnResponse.ideas().isEmpty()) {
                log.warn("⚠️ [IDEA_EXTRACTOR] Response status is success but no ideas found");
                return Mono.just(Collections.emptyList());
            }

            final List<Idea> ideas = nnResponse.ideas()
                    .stream()
                    .map(idea -> new Idea(
                            idea.id(),
                            idea.title(),
                            idea.description()))
                    .toList();

            return Mono.just(ideas);

        } catch (final JsonProcessingException e) {
            log.error("❌ [IDEA_EXTRACTOR] Failed to parse JSON response", e);
            return Mono.just(Collections.emptyList());
        }
    }

    /**
     * Извлечь строки идей для сохранения в контекст.
     *
     * @param ideas список идей
     * @return список строк идей
     */
    private List<String> extractIdeaStrings(final List<Idea> ideas) {
        return ideas.stream()
                .map(idea -> {
                    final String ideaString = idea.title() + ": " + idea.description();
                    log.debug("💡 [IDEA_EXTRACTOR] Converting to string: {}", ideaString);
                    return ideaString;
                })
                .toList();
    }

    /**
     * Извлечь JSON из markdown ответа.
     *
     * @param content содержимое ответа
     * @return JSON строка
     */
    private String extractJsonFromMarkdown(final String content) {
        if (content.contains(JSON_MARKDOWN)) {
            final int start = content.indexOf(JSON_MARKDOWN) + JSON_MARKDOWN.length();
            final int end = content.indexOf(MARKDOWN_CODE, start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        if (content.contains(MARKDOWN_CODE)) {
            final int jsonStart = content.indexOf(MARKDOWN_CODE) + MARKDOWN_CODE.length();
            final int jsonEnd = content.indexOf(MARKDOWN_CODE, jsonStart);
            if (jsonEnd > jsonStart) {
                return content.substring(jsonStart, jsonEnd).trim();
            }
        }
        return content.trim();
    }
}
