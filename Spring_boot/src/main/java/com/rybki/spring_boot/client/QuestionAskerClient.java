package com.rybki.spring_boot.client;

import org.springframework.stereotype.Component;

import com.rybki.spring_boot.llm.contract.LlmClient;
import com.rybki.spring_boot.llm.contract.LlmRequest;
import com.rybki.spring_boot.llm.contract.LlmResponse;
import com.rybki.spring_boot.service.LlmRequestFactoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
@Slf4j
public class QuestionAskerClient {

    private final LlmClient llmClient;
    private final LlmRequestFactoryService requestFactoryService;
    
    public Mono<String> askQuestion(final String question) {
        final LlmRequest request = requestFactoryService.createAskRequest(question);

        return llmClient.sendRequest(request)
                .flatMap(this::parseResponse)
                .doOnSuccess(responseContent -> {
                    log.info("✅ [QUESTION_ASKER] Received response for the question");
                })
                .onErrorResume(e -> {
                    log.error("❌ [QUESTION_ASKER] Error while asking question: {}", e.getMessage());
                    return Mono.just("Error: Unable to get response from LLM.");
                });
    }

    private Mono<String> parseResponse(final LlmResponse response) {
        return Mono.just(response.getContent());
    }
}
