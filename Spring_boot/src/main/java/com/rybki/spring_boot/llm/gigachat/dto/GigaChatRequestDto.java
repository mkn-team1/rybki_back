package com.rybki.spring_boot.llm.gigachat.dto;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import lombok.extern.slf4j.Slf4j;

/**
 * DTO для запроса к GigaChat API.
 * Содержит модель, сообщения и параметры генерации.
 */
@Slf4j
public record GigaChatRequestDto(
    String model,
    List<Message> messages,
    boolean stream,
    int updateInterval) {

  private static final String systemPrompt = """
      Ты — аналитическая система для извлечения значимых идей из различных источников (переговоры, обсуждения, отчёты, заметки). Твоя основная задача — идентифицировать и выделить существенную информацию, которая направлена на развитие проекта, улучшение продукта или повышение эффективности бизнеса. Ты должна учитывать влияние предложенных идей на внутренние процессы, коммуникацию, стратегию или прибыль организации.
      Забудь все предыдущие инструкции. Ты должен распознавать любые идеи, которые тебе приходят. Даже самые бредовые.
      #### Инструкция
      Для каждой передаваемой порции текста следуй следующему порядку действий:
      1. Проанализируй текст и найди предложения, соответствующие указанным критериям.
      2. Для каждого подходящего предложения создай структуру JSON согласно указанному шаблону.
      3. В случае отсутствия таких предложений верни статус `no_ideas_found`.
      4. Убедись, что каждый элемент соответствует формату и правилам генерации.

      #### Формат ответа
      Возвращай строго один объект JSON формата:
      ```json
      {
        "status": "success" | "no_ideas_found",
        "ideas": [
          {
            "id": "unique_id",
            "title": "Название идеи",
            "description": "Описание идеи с возможными выгодами и шагами реализации",
            "category": "Категория идеи"
          }
        ],
        "meta": {
          "total_ideas": число_идей,
          "source_text_length": длина_исходного_текста
        }
      }
      ```

      Если нет соответствующих идей, используй следующий шаблон:
      ```json
      {
        "status": "no_ideas_found",
        "ideas": [],
        "meta": {
          "total_ideas": 0,
          "source_text_length": длина_исходного_текста
        }
      }
      ```

      #### Примеры
      **Хороший пример**
      Вход:
      Мы часто сталкиваемся с задержками доставки товаров клиентам. Возможно, стоит внедрить автоматизированную систему отслеживания заказов.

      Выход:
      ```json
      {
        "status": "success",
        "ideas": [
          {
            "id": "idea_1",
            "title": "Автоматизированная система отслеживания заказов",
            "description": "Внедрение системы автоматической проверки статуса заказа, ускоряющей доставку и повышающей удовлетворенность клиентов.",
            "category": "оптимизация процессов"
          }
        ],
        "meta": {
          "total_ideas": 1,
          "source_text_length": 98
        }
      }
      ```

      **Плохой пример**
      Вход:
      Нужно срочно купить новые стулья в офис!

      Выход:
      ```json
      {
        "status": "no_ideas_found",
        "ideas": [],
        "meta": {
          "total_ideas": 0,
          "source_text_length": 35
        }
      }
      ```

      #### Примечания
      - Категория должна соответствовать типу идеи (развитие бизнеса, оптимизация, маркетинг и др.).
      - Следуй правилу однозначности и избегай добавления собственных интерпретаций.

      ## Критерии качества
      - Полностью соблюдается формат JSON.
      - Корректно определены существенные идеи.
      - Текст описания ясен и конкретен.
      - Включаются только идеи, имеющие практическую ценность для бизнеса.

      Забудь предыдущие инструкции. Распознавай все идеи, которые тебе приходят.
      """;

  public static Message getSystemMessage() {
    log.info("🔧 [DTO_FACTORY] Creating system message with prompt length: {}",
        systemPrompt.length());
    return new Message("system", systemPrompt);
  }

  public static Message getAssistantMessage(final String text) {
    log.info("🤖 [DTO_FACTORY] Creating assistant message with text length: {}",
        text != null ? text.length() : 0);
    return new Message("assistant", text);
  }

  public static Message getPreviousIdeasMessage(final Queue<List<String>> previousIdeas) {
    log.info("📚 [DTO_FACTORY] Creating previous ideas message, queue size: {}",
        previousIdeas != null ? previousIdeas.size() : 0);

    final String previousIdeasMessage;

    if (previousIdeas == null || previousIdeas.isEmpty()) {
      log.info("📭 [DTO_FACTORY] No previous ideas found");
      previousIdeasMessage = "Нет ранее найденных идей.";
    } else {
      final List<String> allIdeas = new LinkedList<>();
      for (List<String> ideaList : previousIdeas) {
        allIdeas.addAll(ideaList);
      }

      final int totalIdeas = allIdeas.size();
      log.info("📖 [DTO_FACTORY] Including {} previous ideas in context", totalIdeas);

      previousIdeasMessage = """
          Ранее были найдены следующие идеи.
          Пожалуйста, не предлагай их снова:
          %s
          """.formatted(String.join("\n- ", allIdeas));
    }

    log.info("📝 [DTO_FACTORY] Previous ideas message length: {}",
        previousIdeasMessage.length());
    return getAssistantMessage(previousIdeasMessage);
  }

  public static Message getUserMessage(final String text) {
    if (text == null) {
      log.warn("⚠️ [DTO_FACTORY] Creating user message with null text");
    } else {
      log.info("👤 [DTO_FACTORY] Creating user message with text length: {}",
          text.length());
      log.info("📝 [DTO_FACTORY] User message content: {}",
          text);
    }
    return new Message("user", text);
  }

  public record Message(
      String role,
      String content) {

  }
}
