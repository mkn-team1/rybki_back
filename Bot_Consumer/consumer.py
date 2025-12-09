import json
import logging
import os

import requests
from dotenv import load_dotenv
from kafka import KafkaConsumer

load_dotenv()

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

KAFKA_BOOTSTRAP_SERVERS = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'kafka:29092')
BACKEND_BASE_URL = os.getenv('BACKEND_BASE_URL', 'http://spring-backend:8080')
KAFKA_TOPIC = 'bot-commands'
KAFKA_GROUP_ID = 'bot-consumer-group'

consumer = KafkaConsumer(
    KAFKA_TOPIC,
    bootstrap_servers=[KAFKA_BOOTSTRAP_SERVERS],
    group_id=KAFKA_GROUP_ID,
    value_deserializer=lambda m: json.loads(m.decode('utf-8')),
    auto_offset_reset='earliest',
    enable_auto_commit=True
)

logger.info(f"Consumer started. Listening to topic '{KAFKA_TOPIC}' from {KAFKA_BOOTSTRAP_SERVERS}")

def handle_connect_bot_command(command):
    """
    Обрабатывает команду подключения бота
    {
        "botId": "uuid",
        "talkLink": "https://...",
        "platform": "kontur_talk"
    }
    """
    bot_id = command.get('botId')
    talk_link = command.get('talkLink')
    platform = command.get('platform')
    
    if not bot_id or not talk_link:
        logger.error(f"Invalid command: missing botId or talkLink. Command: {command}")
        return
    
    logger.info(f"Processing bot connection: botId={bot_id}, platform={platform}")
    
    # Здесь можно запустить бота (например, Browser_bot)
    # Для сейчас просто логируем и отправляем уведомление бэку
    
    try:
        # Отправляем уведомление в бэк, что бот запустился
        response = requests.post(
            f"{BACKEND_BASE_URL}/bot/{bot_id}/started",
            json={"talkLink": talk_link, "platform": platform},
            timeout=5
        )
        response.raise_for_status()
        logger.info(f"Bot {bot_id} started successfully")
    except Exception as e:
        logger.error(f"Failed to notify backend about bot {bot_id}: {e}")

try:
    for message in consumer:
        try:
            command = message.value
            logger.info(f"Received command from topic '{KAFKA_TOPIC}': {command}")
            
            handle_connect_bot_command(command)
        except Exception as e:
            logger.error(f"Error processing message: {e}")
except KeyboardInterrupt:
    logger.info("Consumer interrupted")
finally:
    consumer.close()
    logger.info("Consumer closed")
