# LLM Abstraction Architecture

## 📋 Обзор

Реализована абстракция для работы с различными LLM провайдерами (GigaChat, OpenAI, Claude и т.д.). Архитектура построена на интерфейсах, что позволяет легко добавлять новых провайдеров без изменения основного кода приложения.

## 🏗️ Структура

```
com.rybki.spring_boot.llm
├── contract/
│   ├── LlmAuthProvider.java      # Интерфейс аутентификации
│   ├── LlmClient.java             # Интерфейс клиента для отправки запросов
│   ├── LlmRequest.java            # Интерфейс для LLM запроса
│   └── LlmResponse.java           # Интерфейс для LLM ответа
└── gigachat/
    ├── GigaChatAuthProvider.java  # Реализация аутентификации для GigaChat
    ├── GigaChatLlmClient.java     # Реализация клиента для GigaChat
    └── dto/
        ├── GigaChatRequestDto.java   # DTO запроса GigaChat
        ├── GigaChatResponseDto.java  # DTO ответа GigaChat
        ├── GigaChatTokenDto.java     # DTO токена GigaChat
        └── NnResponseDto.java        # DTO ответа от NN (идеи)
```

## 🔄 Использование

### 1. Создание запроса через сервис

```java
LlmRequest request = llmRequestFactoryService.createIdeaExtractionRequest(text);
```

### 2. Отправка запроса клиентом

```java
Mono<LlmResponse> response = llmClient.sendRequest(request);
```

### 3. Обработка ответа

```java
response.flatMap(resp -> {
    String content = resp.getContent();
    String model = resp.getModel();
    LlmResponse.UsageInfo usage = resp.getUsageInfo();
    // Обработка ответа
});
```

## 🔐 Аутентификация

- **LlmAuthProvider** - управляет токенами доступа
- **GigaChatAuthProvider** - реализация для GigaChat
  - Автоматически обновляет токен при истечении
  - Планировщик обновления каждые 30 минут

## ➕ Добавление нового провайдера

Для поддержки нового провайдера (например, OpenAI):

1. Создайте папку `openai/` в `llm/`
2. Создайте классы, реализующие интерфейсы:
   - `OpenAiAuthProvider implements LlmAuthProvider`
   - `OpenAiLlmClient implements LlmClient`
3. Добавьте DTO классы в `openai/dto/`
4. Зарегистрируйте в Spring как `@Service`

**Пример:**

```java
@Service
public class OpenAiAuthProvider implements LlmAuthProvider {
    // Реализация для OpenAI
}

@Service
public class OpenAiLlmClient implements LlmClient {
    // Реализация для OpenAI
}
```

## 🎯 Интерфейсы

### LlmAuthProvider
```java
Mono<String> getAccessToken();
Mono<String> refreshToken();
```

### LlmClient
```java
Mono<LlmResponse> sendRequest(LlmRequest request);
String getProviderName();
```

### LlmRequest
```java
List<Message> getMessages();
GenerationParams getGenerationParams();

interface Message {
    String getRole();
    String getContent();
}

interface GenerationParams {
    boolean isStream();
    int getUpdateInterval();
}
```

### LlmResponse
```java
String getContent();
String getModel();
UsageInfo getUsageInfo();

interface UsageInfo {
    int getPromptTokens();
    int getCompletionTokens();
    int getTotalTokens();
}
```

## 🔄 Миграция с GigaChatAuthService

**Старое:**
```java
@Autowired
GigaChatAuthService authService;
```

**Новое:**
```java
@Autowired
LlmAuthProvider authProvider;  // или @Autowired GigaChatAuthProvider

// Провайдер выбирается автоматически в зависимости от конфигурации
```

## 📚 Сервисы

### LlmRequestFactoryService
Создает LLM запросы с управлением контекстом диалога:
- `createIdeaExtractionRequest(text)` - создание запроса для извлечения идей
- `addFoundIdeas(ideas)` - добавление найденных идей в контекст

### IdeaExtractorClient
Отправляет запросы и обрабатывает ответы:
- `extractIdeas(text)` - извлечение идей из текста
- Использует `LlmClient` для отправки запросов
- Парсит ответ и преобразует в доменные объекты

## ✅ Преимущества архитектуры

1. **Слабая связанность** - легко менять реализацию провайдера
2. **Тестируемость** - можно подменять реализации на моки
3. **Расширяемость** - новые провайдеры добавляются без изменения старого кода
4. **Единообразный интерфейс** - одинаковый способ работы с разными провайдерами
