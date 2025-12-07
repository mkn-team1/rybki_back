# LLM Abstraction - Migration Guide

## 📋 Обзор миграции

Выполнена полная рефакторизация системы работы с LLM провайдерами. Теперь приложение использует абстракцию, которая позволяет легко переключаться между различными LLM провайдерами без изменения основного кода.

## 🔄 Что изменилось

### До миграции
```java
// Привязка к конкретной реализации
@Autowired
GigaChatAuthService authService;

@Autowired
DtoRequestFactoryService requestFactory;
```

### После миграции
```java
// Привязка к интерфейсам
@Autowired
LlmAuthProvider authProvider;

@Autowired
LlmRequestFactoryService requestFactory;
```

## 📁 Структура пакетов

### Новая структура
```
com.rybki.spring_boot.llm
├── contract/                           ← Интерфейсы (новое)
│   ├── LlmAuthProvider.java
│   ├── LlmClient.java
│   ├── LlmRequest.java
│   └── LlmResponse.java
└── gigachat/                           ← GigaChat реализация (переорганизовано)
    ├── GigaChatAuthProvider.java       ← было GigaChatAuthService
    ├── GigaChatLlmClient.java          ← новое
    └── dto/
        ├── GigaChatRequestDto.java
        ├── GigaChatResponseDto.java
        ├── GigaChatTokenDto.java
        └── NnResponseDto.java
```

## 🗑️ Удаленные файлы

| Удаленный файл | Заменён на |
|---|---|
| `service/GigaChatAuthService.java` | `llm/gigachat/GigaChatAuthProvider.java` |
| `service/DtoRequestFactoryService.java` | `service/LlmRequestFactoryService.java` |
| `model/dto/GigaChatRequestDto.java` | `llm/gigachat/dto/GigaChatRequestDto.java` |
| `model/dto/GigaChatResponseDto.java` | `llm/gigachat/dto/GigaChatResponseDto.java` |
| `model/dto/GigaChatTokenDto.java` | `llm/gigachat/dto/GigaChatTokenDto.java` |
| `model/dto/NnResponseDto.java` | `llm/gigachat/dto/NnResponseDto.java` |

## 📝 Обновления в коде

### IdeaExtractorClient

**Было:**
```java
@Component
@RequiredArgsConstructor
public class IdeaExtractorClient {
    private final GigaChatAuthService authService;
    private final DtoRequestFactoryService dtoRequestFactoryService;
    
    public Mono<List<Idea>> extractIdeas(final String text) {
        final GigaChatRequestDto request = 
            dtoRequestFactoryService.createIdeaExtractionRequest(text);
        
        return authService.getAccessToken()
            .flatMap(accessToken -> {
                // отправка к GigaChat
            });
    }
}
```

**Стало:**
```java
@Component
@RequiredArgsConstructor
public class IdeaExtractorClient {
    private final LlmClient llmClient;
    private final LlmRequestFactoryService requestFactory;
    
    public Mono<List<Idea>> extractIdeas(final String text) {
        final LlmRequest request = 
            requestFactory.createIdeaExtractionRequest(text);
        
        return llmClient.sendRequest(request)
            .flatMap(this::parseResponse);
    }
}
```

### LlmRequestFactoryService (новый)

```java
@Service
public class LlmRequestFactoryService {
    
    public LlmRequest createIdeaExtractionRequest(final String text) {
        // Создание LlmRequest вместо GigaChatRequestDto
    }
    
    public void addFoundIdeas(final List<String> ideas) {
        // Добавление идей в контекст
    }
}
```

## 🔌 Интерфейсы

### LlmAuthProvider
```java
public interface LlmAuthProvider {
    Mono<String> getAccessToken();
    Mono<String> refreshToken();
}
```

### LlmClient
```java
public interface LlmClient {
    Mono<LlmResponse> sendRequest(LlmRequest request);
    String getProviderName();
}
```

### LlmRequest
```java
public interface LlmRequest {
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
}
```

### LlmResponse
```java
public interface LlmResponse {
    String getContent();
    String getModel();
    UsageInfo getUsageInfo();
    
    interface UsageInfo {
        int getPromptTokens();
        int getCompletionTokens();
        int getTotalTokens();
    }
}
```

## 🚀 Добавление нового провайдера

Процесс добавления нового провайдера (например, OpenAI) теперь значительно упрощен:

### 1. Создайте пакет
```
mkdir -p src/main/java/com/rybki/spring_boot/llm/openai/dto
```

### 2. Реализуйте интерфейсы
```java
@Service
public class OpenAiAuthProvider implements LlmAuthProvider {
    @Override
    public Mono<String> getAccessToken() { ... }
    
    @Override
    public Mono<String> refreshToken() { ... }
}

@Service
public class OpenAiLlmClient implements LlmClient {
    @Override
    public Mono<LlmResponse> sendRequest(LlmRequest request) { ... }
    
    @Override
    public String getProviderName() { return "OpenAI"; }
}
```

### 3. Добавьте DTO классы
Создайте специфичные для OpenAI DTO классы в `openai/dto/`.

### 4. Обновите конфигурацию
```yaml
openai:
  api:
    key: ${OPENAI_API_KEY}
  model: gpt-4
```

### 5. ВСЕ!
Весь остальной код автоматически будет работать с новым провайдером.

## ✅ Проверка список

При внедрении этих изменений убедитесь:

- [ ] Удалены все импорты старых классов (`GigaChatAuthService`, `DtoRequestFactoryService`)
- [ ] Обновлены все зависимости на новые интерфейсы
- [ ] Все классы компилируются без ошибок
- [ ] Тесты проходят успешно
- [ ] Приложение запускается и работает
- [ ] GigaChat остается основным провайдером до добавления других

## 📚 Дополнительная документация

- `LLM_ARCHITECTURE.md` - Подробная архитектура
- `LLM_DIAGRAM.txt` - Диаграмма взаимодействия
- `EXAMPLE_NEW_PROVIDER.md` - Полный пример добавления OpenAI

## 🎯 Преимущества новой архитектуры

1. **Слабая связанность** - зависимости от интерфейсов
2. **Расширяемость** - новые провайдеры добавляются отдельно
3. **Тестируемость** - легко создавать мок-объекты
4. **Чистота кода** - каждый класс отвечает за одно
5. **Масштабируемость** - легко переключаться между провайдерами

## ⚠️ Важные замечания

1. **GigaChat остается по умолчанию** - все текущие конфигурации остаются без изменений
2. **Обратная совместимость** - все API остаются теми же
3. **Коммуникация** - убедитесь, что вся команда знает о новой структуре
4. **Документация** - всегда актуализируйте документацию при добавлении провайдеров

## 🔗 Связанные файлы

- `com/rybki/spring_boot/llm/contract/` - интерфейсы абстракции
- `com/rybki/spring_boot/llm/gigachat/` - текущая реализация
- `com/rybki/spring_boot/service/LlmRequestFactoryService.java` - создание запросов
- `com/rybki/spring_boot/client/IdeaExtractorClient.java` - обработка ответов

---

**Последнее обновление:** December 7, 2025
**Статус:** ✅ Завершено
