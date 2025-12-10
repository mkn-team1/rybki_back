package com.rybki.spring_boot.llm.contract;

import java.util.List;

/**
 * Интерфейс для LLM запроса.
 * Позволяет абстрагироваться от конкретного формата запроса провайдера.
 */
public interface LlmRequest {

    /**
     * Получить список сообщений в формате провайдера.
     *
     * @return список сообщений
     */
    List<Message> getMessages();

    /**
     * Получить параметры генерации (температура, максимальное количество токенов и
     * т.д.).
     *
     * @return параметры генерации
     */
    GenerationParams getGenerationParams();

    /**
     * Интерфейс для сообщения.
     */
    interface Message {
        String getRole();

        String getContent();
    }

    /**
     * Интерфейс для параметров генерации.
     */
    interface GenerationParams {
        boolean isStream();

        int getUpdateInterval();
    }
}
