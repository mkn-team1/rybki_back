package com.rybki.spring_boot.llm.contract;

import reactor.core.publisher.Mono;

/**
 * Интерфейс для LLM клиента.
 * Позволяет отправлять запросы к различным LLM провайдерам.
 */
public interface LlmClient {

    /**
     * Отправить запрос к LLM.
     *
     * @param request запрос к LLM
     * @return Mono с ответом от LLM
     */
    Mono<LlmResponse> sendRequest(LlmRequest request);

    /**
     * Получить название провайдера (GigaChat, OpenAI, Claude и т.д.).
     *
     * @return название провайдера
     */
    String getProviderName();
}
