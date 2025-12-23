package com.rybki.spring_boot.llm.contract;

import reactor.core.publisher.Mono;

/**
 * Интерфейс для управления аутентификацией с LLM провайдерами.
 * Позволяет абстрагироваться от конкретной реализации провайдера (GigaChat,
 * OpenAI и т.д.).
 */
public interface LlmAuthProvider {

    /**
     * Получить токен доступа для работы с LLM API.
     * Если токен истёк, автоматически обновит его.
     *
     * @return Mono с токеном доступа
     */
    Mono<String> getAccessToken();

    /**
     * Явно обновить токен доступа.
     *
     * @return Mono с новым токеном доступа
     */
    Mono<String> refreshToken();
}
