package com.rybki.spring_boot.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.rybki.spring_boot.llm.contract.LlmClient;
import com.rybki.spring_boot.llm.contract.LlmRequest;
import com.rybki.spring_boot.llm.contract.LlmResponse;
import com.rybki.spring_boot.model.domain.redis.Idea;
import com.rybki.spring_boot.model.domain.redis.IdeaStatus;
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

        // 1. Получаем идеи реактивно и сортируем по времени создания
        Mono<List<Idea>> ideasMono = Mono.fromCallable(() -> {
            // Берем все id из pending, accepted и rejected
            Set<String> pendingIds = ideaRepository.getPendingIdeas(eventId);
            Set<String> acceptedIds = ideaRepository.getAcceptedIdeas(eventId);
            Set<String> rejectedIds = ideaRepository.getRejectedIdeas(eventId);

            log.info("📋 [SUMMARY] Redis sets for eventId={}: pending={}, accepted={}, rejected={}", 
                    eventId, pendingIds.size(), acceptedIds.size(), rejectedIds.size());
            log.info("📋 [SUMMARY] Pending IDs: {}", pendingIds);
            log.info("📋 [SUMMARY] Accepted IDs: {}", acceptedIds);
            log.info("📋 [SUMMARY] Rejected IDs: {}", rejectedIds);

            Stream<Idea> ideaStream = Stream.of(pendingIds, acceptedIds, rejectedIds)
                .flatMap(Set::stream)
                .distinct()
                .map(ideaRepository::findIdeaById)
                .filter(Optional::isPresent)
                .map(Optional::get);

            // accepted_only: оставляем только GLOBAL/GOLDEN (принятые/продвинутые идеи)
            if ("accepted_only".equals(mode)) {
                ideaStream = ideaStream
                        .filter(i -> i.getStatus() == IdeaStatus.GOLDEN || i.getStatus() == IdeaStatus.GLOBAL);
            } else if (!"all".equals(mode)) {
                throw new IllegalArgumentException("Unsupported mode: " + mode);
            }

            return ideaStream
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .collect(Collectors.toList());
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
                """
                На основе списка идей участников составь %s резюме обсуждения на русском языке.
                
                Строго соблюдай:
                1. Используй только идеи из предоставленного списка
                2. Не добавляй свои комментарии, выводы или предположения
                3. Излагай нейтрально, без вводных фраз и эмоций
                4. Обобщай схожие идеи, но не перечисляй их дословно
                
                Формат:
                - Краткое обобщение (1-2 предложения)
                - Основные темы или выводы (по пунктам)
                
                Список идей:
                %s
                """,
                style != null ? style : "развёрнутое",
                inputText
            );

            // 3. Формируем LLM request через фабрику
            LlmRequest request = llmRequestFactoryService.createCustomRequest(prompt);

            // 4. Отправляем на LLM и возвращаем результат
            return llmClient.sendRequest(request)
                    .map(LlmResponse::getContent)
                    .doOnSuccess(s -> {
                        String logContent = s != null && s.length() > 200 ? s.substring(0, 200) + "..." : s;
                        log.info("✅ [SUMMARY] Generated summary: {}", logContent);
                    })
                    .doOnError(e -> log.error("❌ [SUMMARY] Failed to generate summary for eventId={}", eventId, e));
        });
    }
}
