package com.rybki.spring_boot.llm.gigachat;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.rybki.spring_boot.llm.contract.LlmClient;
import com.rybki.spring_boot.llm.contract.LlmRequest;
import com.rybki.spring_boot.llm.contract.LlmResponse;
import com.rybki.spring_boot.llm.gigachat.dto.GigaChatResponseDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Реализация LLM клиента для GigaChat.
 * Отправляет запросы к GigaChat API и обрабатывает ответы.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GigaChatLlmClient implements LlmClient {

    private static final String PROVIDER_NAME = "GigaChat";

    private final WebClient webClient = WebClient.builder().build();
    private final GigaChatAuthProvider authProvider;

    @Value("${gigachat.api.url}")
    private String apiUrl;

    @Value("${spring.data.ideaExtractorTimeout}")
    private Integer timeoutSeconds;

    @Override
    public Mono<LlmResponse> sendRequest(final LlmRequest request) {
        log.debug("📤 [GIGACHAT] Sending request to GigaChat API");

        final GigaChatRequestBuilder requestBuilder = new GigaChatRequestBuilder(request);
        final Object gigaChatRequest = requestBuilder.build();

        return authProvider.getAccessToken()
                .flatMap(accessToken -> {
                    if (accessToken == null || accessToken.isEmpty()) {
                        log.warn("❌ [GIGACHAT] No access token available");
                        return Mono.error(new RuntimeException("No access token available"));
                    }

                    return webClient.post()
                            .uri(apiUrl)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + accessToken)
                            .bodyValue(gigaChatRequest)
                            .retrieve()
                            .bodyToMono(GigaChatResponseDto.class)
                            .timeout(Duration.ofSeconds(timeoutSeconds))
                            .doOnNext(response -> log.debug("📥 [GIGACHAT] Response: {}", response))
                            .map(GigaChatResponseAdapter::new)
                            .doOnSuccess(response -> log.info("✅ [GIGACHAT] Request processed successfully"))
                            .doOnError(e -> log.error("❌ [GIGACHAT] Request failed: {}", e.getMessage(), e));
                });
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * Конвертирует LlmRequest в объект для отправки GigaChat API.
     */
    private static class GigaChatRequestBuilder {

        private final LlmRequest request;

        GigaChatRequestBuilder(final LlmRequest request) {
            this.request = request;
        }

        Object build() {
            final List<MessageDto> messages = request.getMessages()
                    .stream()
                    .map(msg -> new MessageDto(msg.getRole(), msg.getContent()))
                    .toList();

            final LlmRequest.GenerationParams params = request.getGenerationParams();

            return new GigaChatRequestPayload(
                    GigaChatLlmClient.PROVIDER_NAME,
                    messages,
                    params.isStream(),
                    params.getUpdateInterval());
        }

        private record MessageDto(String role, String content) {
        }

        private record GigaChatRequestPayload(
                String model,
                List<MessageDto> messages,
                boolean stream,
                int updateInterval) {
        }
    }

    /**
     * Адаптер для преобразования GigaChatResponseDto в LlmResponse.
     */
    private static class GigaChatResponseAdapter implements LlmResponse {

        private final GigaChatResponseDto response;

        GigaChatResponseAdapter(final GigaChatResponseDto response) {
            this.response = response;
        }

        @Override
        public String getContent() {
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return "";
            }
            return response.choices().getFirst().message().content();
        }

        @Override
        public String getModel() {
            return response.model();
        }

        @Override
        public UsageInfo getUsageInfo() {
            return new UsageInfoAdapter(response.usage());
        }

        private static class UsageInfoAdapter implements UsageInfo {

            private final GigaChatResponseDto.Usage usage;

            UsageInfoAdapter(final GigaChatResponseDto.Usage usage) {
                this.usage = usage;
            }

            @Override
            public int getPromptTokens() {
                return usage.promptTokens();
            }

            @Override
            public int getCompletionTokens() {
                return usage.completionTokens();
            }

            @Override
            public int getTotalTokens() {
                return usage.totalTokens();
            }
        }
    }
}
