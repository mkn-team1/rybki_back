# Bot Consumer

Сервис для обработки команд из Kafka топика `bot-commands` и запуска ботов.

## Как это работает

1. **Spring Backend** отправляет сообщение в Kafka топик `bot-commands` когда нужно подключить бота:
```json
{
  "botId": "550e8400-e29b-41d4-a716-446655440000",
  "talkLink": "https://talk.kontur.ru/meeting/12345",
  "platform": "kontur_talk"
}
```

2. **Bot Consumer** читает сообщение из Kafka и:
   - Парсит команду
   - Запускает бота (запускает Browser_bot контейнер или отправляет команду)
   - Отправляет уведомление в бэк на эндпоинт `POST /bot/{botId}/started`

3. **Spring Backend** получает уведомление и отправляет в WebSocket клиенту, что бот готов

## Environment переменные

- `KAFKA_BOOTSTRAP_SERVERS` - адрес Kafka (по умолчанию `kafka:29092`)
- `BACKEND_BASE_URL` - адрес Spring Backend (по умолчанию `http://spring-backend:8080`)

## Запуск локально (без Docker)

```bash
pip install -r requirements.txt
python consumer.py
```

## Запуск через Docker Compose

```bash
docker-compose up bot-consumer
```

## Структура сообщения из Kafka

Topic: `bot-commands`

```json
{
  "botId": "string (UUID)",
  "talkLink": "string (URL встречи)",
  "platform": "string (kontur_talk)"
}
```

## Структура ответа бэку

Endpoint: `POST /bot/{botId}/started`

```json
{
  "talkLink": "string",
  "platform": "string"
}
```
