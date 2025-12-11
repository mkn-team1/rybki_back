// ПРИМЕР: Как добавить поддержку OpenAI
// =====================================================
// Этот файл показывает, как легко добавить новый LLM провайдер

// Шаг 1: Создать пакет
// mkdir -p src/main/java/com/rybki/spring_boot/llm/openai/dto

// Шаг 2: Создать DTO классы (пример для OpenAI)

// ==== ФАЙЛ: llm/openai/dto/OpenAiTokenDto.java ====
//
// package com.rybki.spring_boot.llm.openai.dto;
//
// import com.fasterxml.jackson.annotation.JsonProperty;
//
// public record OpenAiTokenDto(
//     @JsonProperty("access_token")
//     String accessToken
// ) {
// }


// ==== ФАЙЛ: llm/openai/dto/OpenAiRequestDto.java ====
//
// package com.rybki.spring_boot.llm.openai.dto;
//
// import java.util.List;
//
// public record OpenAiRequestDto(
//     String model,
//     List<Message> messages,
//     double temperature,
//     int maxTokens
// ) {
//     public record Message(String role, String content) {
//     }
// }


// ==== ФАЙЛ: llm/openai/dto/OpenAiResponseDto.java ====
//
// package com.rybki.spring_boot.llm.openai.dto;
//
// import java.util.List;
//
// public record OpenAiResponseDto(
//     String id,
//     String object,
//     long created,
//     String model,
//     List<Choice> choices,
//     Usage usage
// ) {
//     public record Choice(
//         int index,
//         Message message,
//         String finishReason
//     ) {
//     }
//
//     public record Message(
//         String role,
//         String content
//     ) {
//     }
//
//     public record Usage(
//         int promptTokens,
//         int completionTokens,
//         int totalTokens
//     ) {
//     }
// }


// Шаг 3: Создать реализацию LlmAuthProvider

// ==== ФАЙЛ: llm/openai/OpenAiAuthProvider.java ====
//
// package com.rybki.spring_boot.llm.openai;
//
// import com.rybki.spring_boot.llm.contract.LlmAuthProvider;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Service;
// import reactor.core.publisher.Mono;
//
// @Service
// @RequiredArgsConstructor
// @Slf4j
// public class OpenAiAuthProvider implements LlmAuthProvider {
//
//     @Value("${openai.api.key}")
//     private String apiKey;
//
//     @Override
//     public Mono<String> getAccessToken() {
//         // OpenAI использует простой Bearer token из конфигурации
//         if (apiKey == null || apiKey.isEmpty()) {
//             return Mono.error(new RuntimeException("OpenAI API key not configured"));
//         }
//         return Mono.just(apiKey);
//     }
//
//     @Override
//     public Mono<String> refreshToken() {
//         // OpenAI не требует обновления токена
//         return getAccessToken();
//     }
// }


// Шаг 4: Создать реализацию LlmClient

// ==== ФАЙЛ: llm/openai/OpenAiLlmClient.java ====
//
// package com.rybki.spring_boot.llm.openai;
//
// import java.time.Duration;
// import java.util.List;
//
// import com.rybki.spring_boot.llm.contract.LlmClient;
// import com.rybki.spring_boot.llm.contract.LlmRequest;
// import com.rybki.spring_boot.llm.contract.LlmResponse;
// import com.rybki.spring_boot.llm.openai.dto.OpenAiRequestDto;
// import com.rybki.spring_boot.llm.openai.dto.OpenAiResponseDto;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.http.MediaType;
// import org.springframework.stereotype.Service;
// import org.springframework.web.reactive.function.client.WebClient;
// import reactor.core.publisher.Mono;
//
// @Service
// @RequiredArgsConstructor
// @Slf4j
// public class OpenAiLlmClient implements LlmClient {
//
//     private static final String PROVIDER_NAME = "OpenAI";
//     private static final String API_URL = "https://api.openai.com/v1/chat/completions";
//
//     private final WebClient webClient = WebClient.builder().build();
//     private final OpenAiAuthProvider authProvider;
//
//     @Value("${openai.model:gpt-4}")
//     private String model;
//
//     @Value("${spring.data.ideaExtractorTimeout}")
//     private Integer timeoutSeconds;
//
//     @Override
//     public Mono<LlmResponse> sendRequest(final LlmRequest request) {
//         log.debug("📤 [OPENAI] Sending request to OpenAI API");
//
//         // Конвертируем LlmRequest в OpenAiRequestDto
//         final OpenAiRequestDto openAiRequest = convertRequest(request);
//
//         return authProvider.getAccessToken()
//             .flatMap(apiKey -> webClient.post()
//                 .uri(API_URL)
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .accept(MediaType.APPLICATION_JSON)
//                 .header("Authorization", "Bearer " + apiKey)
//                 .bodyValue(openAiRequest)
//                 .retrieve()
//                 .bodyToMono(OpenAiResponseDto.class)
//                 .timeout(Duration.ofSeconds(timeoutSeconds))
//                 .map(OpenAiResponseAdapter::new)
//                 .doOnSuccess(response -> log.info("✅ [OPENAI] Request processed successfully"))
//                 .doOnError(e -> log.error("❌ [OPENAI] Request failed: {}", e.getMessage(), e))
//             );
//     }
//
//     @Override
//     public String getProviderName() {
//         return PROVIDER_NAME;
//     }
//
//     private OpenAiRequestDto convertRequest(final LlmRequest request) {
//         final List<OpenAiRequestDto.Message> messages = request.getMessages()
//             .stream()
//             .map(msg -> new OpenAiRequestDto.Message(msg.getRole(), msg.getContent()))
//             .toList();
//
//         return new OpenAiRequestDto(
//             model,
//             messages,
//             0.7, // температура
//             2000 // maxTokens
//         );
//     }
//
//     // Адаптер для преобразования OpenAiResponseDto в LlmResponse
//     private static class OpenAiResponseAdapter implements LlmResponse {
//
//         private final OpenAiResponseDto response;
//
//         OpenAiResponseAdapter(final OpenAiResponseDto response) {
//             this.response = response;
//         }
//
//         @Override
//         public String getContent() {
//             if (response == null || response.choices() == null || response.choices().isEmpty()) {
//                 return "";
//             }
//             return response.choices().getFirst().message().content();
//         }
//
//         @Override
//         public String getModel() {
//             return response.model();
//         }
//
//         @Override
//         public UsageInfo getUsageInfo() {
//             return new UsageInfoAdapter(response.usage());
//         }
//
//         private static class UsageInfoAdapter implements UsageInfo {
//
//             private final OpenAiResponseDto.Usage usage;
//
//             UsageInfoAdapter(final OpenAiResponseDto.Usage usage) {
//                 this.usage = usage;
//             }
//
//             @Override
//             public int getPromptTokens() {
//                 return usage.promptTokens();
//             }
//
//             @Override
//             public int getCompletionTokens() {
//                 return usage.completionTokens();
//             }
//
//             @Override
//             public int getTotalTokens() {
//                 return usage.totalTokens();
//             }
//         }
//     }
// }


// Шаг 5: Добавить конфигурацию в application.yml
//
// openai:
//   api:
//     key: ${OPENAI_API_KEY}
//   model: gpt-4


// =====================================================
// ВСЕ! Теперь приложение поддерживает OpenAI
// =====================================================
//
// Весь остальной код работает без изменений:
// - LlmRequestFactoryService создаёт LlmRequest
// - IdeaExtractorClient отправляет запросы через LlmClient
// - Spring автоматически выбирает нужную реализацию
//
// Если нужно переключиться между провайдерами:
// 1. Активируйте/деактивируйте @Service аннотацию
// 2. ИЛИ используйте @ConditionalOnProperty для выбора по конфиге
//
// Пример с условной загрузкой:
//
// @Service
// @ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
// public class OpenAiAuthProvider implements LlmAuthProvider {
//     ...
// }
//
// @Service
// @ConditionalOnProperty(name = "llm.provider", havingValue = "gigachat", matchIfMissing = true)
// public class GigaChatAuthProvider implements LlmAuthProvider {
//     ...
// }

