package com.rybki.spring_boot.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.rybki.spring_boot.model.dto.GigaChatRequestDto;
import com.rybki.spring_boot.model.dto.GigaChatRequestDto.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DtoRequestFactoryService {

    private static final int MAX_MESSAGES = 4;
    private final Queue<String> lastMessages = new LinkedList<>();
    private final Queue<List<String>> foundIdeas = new LinkedList<>();

    public GigaChatRequestDto createIdeaExtractionRequest(final String text) {
        log.info("🏭 [DTO_FACTORY] Creating request for text: {}", text);
        log.info("📊 [DTO_FACTORY] Current context - lastMessages: {}, foundIdeas: {}",
            lastMessages.size(), foundIdeas.size());

        final List<Message> messages = new LinkedList<>();
        final Message systemPrompt = GigaChatRequestDto.getSystemMessage();
        messages.add(systemPrompt);

        for (String currentMessage : this.lastMessages) {
            messages.add(GigaChatRequestDto.getUserMessage(currentMessage));
        }

        messages.add(GigaChatRequestDto.getUserMessage(text));
        messages.add(GigaChatRequestDto.getPreviousIdeasMessage(foundIdeas));

        lastMessages.add(text);
        if (lastMessages.size() > MAX_MESSAGES) {
            final String removedMessage = lastMessages.poll();
            log.info("🗑️ [DTO_FACTORY] Removed oldest message from context {}, current size: {}", removedMessage,
                lastMessages.size());
        }

        final GigaChatRequestDto request = new GigaChatRequestDto(
            "GigaChat",
            messages,
            false,
            0
        );

        log.info("✅ [DTO_FACTORY] Request created with {} messages total", messages.size());
        log.info("📝 [DTO_FACTORY] Request details - model: {}, stream: {}",
            request.model(), request.stream());

        return request;
    }

    public void addFoundIdeas(final List<String> newIdeas) {
        if (newIdeas == null || newIdeas.isEmpty()) {
            log.info("📭 [DTO_FACTORY] No new ideas to add - list is null or empty");
            return;
        }

        log.info("💾 [DTO_FACTORY] Adding {} new ideas to context", newIdeas.size());
        log.info("📊 [DTO_FACTORY] Current context size before: {}", foundIdeas.size());
        if (foundIdeas.size() > MAX_MESSAGES) {
            final List<String> removedIdea = foundIdeas.poll();
            log.info("🗑️ [DTO_FACTORY] Removed oldest idea: {}",
                removedIdea);
        }

        foundIdeas.add(newIdeas);

        log.info("💡 [DTO_FACTORY] Added ideas to context: {}",
            newIdeas);

        log.info("✅ [DTO_FACTORY] Context updated. Total ideas in memory: {}", foundIdeas.size());
    }
}
