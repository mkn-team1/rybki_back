package com.rybki.spring_boot.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.rybki.spring_boot.llm.contract.LlmClient;
import com.rybki.spring_boot.llm.contract.LlmRequest;
import com.rybki.spring_boot.llm.contract.LlmResponse;
import com.rybki.spring_boot.model.domain.redis.Idea;
import com.rybki.spring_boot.repository.RedisIdeaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class SummaryService {

    private final RedisIdeaRepository ideaRepository;
    private final LlmClient llmClient;
    private final LlmRequestFactoryService llmRequestFactoryService;

    /**
     * Создать summary события.
     *
     * @param eventId - id события
     * @param mode - "all" или "accepted_only"
     * @param style - стиль summary
     * @return Mono с summary text
     */
    public Mono<String> generateSummary(String eventId, String mode, String style) {
        log.info("📋 [SUMMARY] Generating summary for eventId={} with mode={} and style={}", eventId, mode, style);

        // 1. Получаем идеи реактивно
        Mono<List<Idea>> ideasMono = Mono.fromCallable(() -> {
            Stream<Idea> ideaStream;

            switch (mode) {
                case "accepted_only" -> ideaStream = ideaRepository.getAcceptedIdeas(eventId).stream()
                        .map(ideaRepository::findIdeaById)
                        .filter(Optional::isPresent)
                        .map(Optional::get);
                case "all" -> {
                    Stream<Idea> pending = ideaRepository.getPendingIdeas(eventId).stream()
                            .map(ideaRepository::findIdeaById)
                            .filter(Optional::isPresent)
                            .map(Optional::get);
                    Stream<Idea> accepted = ideaRepository.getAcceptedIdeas(eventId).stream()
                            .map(ideaRepository::findIdeaById)
                            .filter(Optional::isPresent)
                            .map(Optional::get);
                    ideaStream = Stream.concat(pending, accepted);
                }
                default -> throw new IllegalArgumentException("Unsupported mode: " + mode);
            }

            return ideaStream.collect(Collectors.toList());
        });

        // 2. Проверяем идеи и формируем prompt
        return ideasMono.flatMap(ideas -> {
            if (ideas.isEmpty()) {
                log.warn("⚠️ [SUMMARY] No ideas found for eventId={}", eventId);
                return Mono.just("No ideas available to summarize.");
            }

            String inputText = ideas.stream()
                    .map(i -> "- " + i.getTitle() + ": " + i.getDescription())
                    .collect(Collectors.joining("\n"));

            String prompt = String.format(
                    "You are a professional conference summarizer. Please create a %s summary of the following ideas:\n%s",
                    style != null ? style : "detailed", inputText
            );

            // 3. Формируем LLM request через фабрику
            LlmRequest request = llmRequestFactoryService.createCustomRequest(prompt);

            // 4. Отправляем на LLM и возвращаем результат
            return llmClient.sendRequest(request)
                    .map(LlmResponse::getContent)
                    .doOnSuccess(s -> log.info("✅ [SUMMARY] Summary generated for eventId={}", eventId))
                    .doOnError(e -> log.error("❌ [SUMMARY] Failed to generate summary for eventId={}", eventId, e));
        });
    }
}
