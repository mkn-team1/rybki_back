package com.rybki.spring_boot.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.springframework.stereotype.Service;

import com.rybki.spring_boot.llm.contract.LlmRequest;
import com.rybki.spring_boot.llm.gigachat.dto.GigaChatRequestDto;

import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для создания LLM запросов.
 * Управляет контекстом диалога и историей найденных идей.
 */
@Service
@Slf4j
public class LlmRequestFactoryService {

    private static final int MAX_MESSAGES = 4;
    private final Queue<String> lastMessages = new LinkedList<>();
    private final Queue<List<String>> foundIdeas = new LinkedList<>();

    /**
     * Создать LLM запрос для извлечения идей из текста.
     *
     * @param text текст для анализа
     * @return LLM запрос
     */
    public LlmRequest createIdeaExtractionRequest(final String text) {
        log.info("🏭 [LLM_REQUEST_FACTORY] Creating idea extraction request");
        log.info("📊 [LLM_REQUEST_FACTORY] Current context - lastMessages: {}, foundIdeas: {}",
                lastMessages.size(), foundIdeas.size());

        final List<GigaChatRequestDto.Message> messages = new LinkedList<>();
        final GigaChatRequestDto.Message systemPrompt = GigaChatRequestDto.getSystemMessage();
        messages.add(systemPrompt);

        if (!foundIdeas.isEmpty()) {
            messages.add(GigaChatRequestDto.getPreviousIdeasMessage(foundIdeas));
        }

        for (final String currentMessage : this.lastMessages) {
            messages.add(GigaChatRequestDto.getUserMessage(currentMessage));
        }

        messages.add(GigaChatRequestDto.getUserMessage(text));

        lastMessages.add(text);
        if (lastMessages.size() > MAX_MESSAGES) {
            final String removedMessage = lastMessages.poll();
            log.info("🗑️ [LLM_REQUEST_FACTORY] Removed oldest message from context {}, current size: {}",
                    removedMessage, lastMessages.size());
        }

        final LlmRequest request = new LlmRequestImpl(messages);

        log.info("✅ [LLM_REQUEST_FACTORY] Request created with {} messages total", messages.size());

        return request;
    }

    /**
     * Добавить найденные идеи в контекст диалога.
     *
     * @param newIdeas список найденных идей
     */
    public void addFoundIdeas(final List<String> newIdeas) {
        if (newIdeas == null || newIdeas.isEmpty()) {
            log.info("📭 [LLM_REQUEST_FACTORY] No new ideas to add - list is null or empty");
            return;
        }

        log.info("💾 [LLM_REQUEST_FACTORY] Adding {} new ideas to context", newIdeas.size());
        log.info("📊 [LLM_REQUEST_FACTORY] Current context size before: {}", foundIdeas.size());

        if (foundIdeas.size() > MAX_MESSAGES) {
            final List<String> removedIdea = foundIdeas.poll();
            log.info("🗑️ [LLM_REQUEST_FACTORY] Removed oldest idea: {}", removedIdea);
        }

        foundIdeas.add(newIdeas);
        log.info("💡 [LLM_REQUEST_FACTORY] Added ideas to context: {}", newIdeas);
        log.info("✅ [LLM_REQUEST_FACTORY] Context updated. Total ideas in memory: {}", foundIdeas.size());
    }

    /**
     * Реализация LlmRequest для работы с сообщениями.
     */
    private static final class LlmRequestImpl implements LlmRequest {

        private final List<Message> messages;

        LlmRequestImpl(final List<GigaChatRequestDto.Message> messages) {
            this.messages = new LinkedList<>(messages.stream()
                    .map(msg -> (LlmRequest.Message) new MessageImpl(msg.role(), msg.content()))
                    .toList());
        }

        @Override
        public List<Message> getMessages() {
            return messages;
        }

        @Override
        public GenerationParams getGenerationParams() {
            return new GenerationParamsImpl();
        }

        private record MessageImpl(String role, String content) implements Message {
            @Override
            public String getRole() {
                return role;
            }

            @Override
            public String getContent() {
                return content;
            }
        }

        private static final class GenerationParamsImpl implements GenerationParams {

            @Override
            public boolean isStream() {
                return false;
            }

            @Override
            public int getUpdateInterval() {
                return 0;
            }
        }
    }
}
