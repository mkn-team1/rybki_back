package com.rybki.spring_boot.llm.contract;

/**
 * Интерфейс для LLM ответа.
 * Позволяет абстрагироваться от конкретного формата ответа провайдера.
 */
public interface LlmResponse {

    /**
     * Получить текст ответа от LLM.
     *
     * @return текст ответа
     */
    String getContent();

    /**
     * Получить модель, которая обработала запрос.
     *
     * @return название модели
     */
    String getModel();

    /**
     * Получить информацию об использовании токенов.
     *
     * @return информация об использовании
     */
    UsageInfo getUsageInfo();

    /**
     * Интерфейс для информации об использовании токенов.
     */
    interface UsageInfo {
        int getPromptTokens();

        int getCompletionTokens();

        int getTotalTokens();
    }
}
