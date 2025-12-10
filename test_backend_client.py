"""
Тестовый клиент для проверки работы backend.
Подключается к Spring Boot WebSocket, отправляет аудио и получает идеи.
"""

import asyncio
import websockets
import wave
import logging
import os
import json

# Настройка логирования
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# Конфигурация
BACKEND_WS_URI = "ws://localhost:8080/ws/client"
AUDIO_FILE = os.path.join("test_audio.wav")
SAMPLE_RATE = 16000
CHUNK_SIZE = int(SAMPLE_RATE * 0.2)  # Отправляем по 200 мс аудио


class BackendClient:
    """Клиент для тестирования backend через WebSocket"""
    
    def __init__(self):
        self.websocket = None
        self.received_ideas = []
        self.client_id = "test_client_123"
        self.event_id = "test_event_456"
    
    async def connect_and_test(self, audio_file: str):
        """Подключается к backend и выполняет полный цикл теста"""
        
        logger.info(f"🔌 Подключаемся к backend: {BACKEND_WS_URI}")
        
        try:
            async with websockets.connect(BACKEND_WS_URI) as websocket:
                self.websocket = websocket
                logger.info("✅ Успешно подключились к backend")
                
                # Запускаем задачу для приёма сообщений
                receive_task = asyncio.create_task(self.receive_messages())
                
                # 1. Отправляем start сообщение
                await self.send_start()
                await asyncio.sleep(0.5)
                
                # 2. Отправляем аудио
                await self.send_audio(audio_file)
                
                # 3. Отправляем end сообщение
                await self.send_end()
                
                # 4. Ждём получения идей
                logger.info("⏳ Ожидаем получения идей от backend...")
                await asyncio.sleep(5)
                
                # Отменяем задачу приёма
                receive_task.cancel()
                try:
                    await receive_task
                except asyncio.CancelledError:
                    pass
                
        except websockets.exceptions.WebSocketException as e:
            logger.error(f"❌ Ошибка WebSocket: {e}")
        except Exception as e:
            logger.error(f"❌ Произошла ошибка: {e}", exc_info=True)
        finally:
            self.websocket = None
    
    async def send_start(self):
        """Отправляет start сообщение для начала сессии"""
        start_message = {
            "type": "start",
            "clientId": self.client_id,
            "eventId": self.event_id
        }
        await self.websocket.send(json.dumps(start_message))
        logger.info(f"📨 Отправлено start сообщение: clientId={self.client_id}, eventId={self.event_id}")
    
    async def send_audio(self, audio_file: str):
        """Отправляет аудио данные в бинарном формате"""
        
        logger.info("📤 Начинаем отправку аудио в backend...")
        
        try:
            with wave.open(audio_file, 'rb') as wf:
                if wf.getframerate() != SAMPLE_RATE or wf.getsampwidth() != 2 or wf.getnchannels() != 1:
                    logger.error(f"Неверный формат аудиофайла: {audio_file}. "
                                 f"Требуется WAV, 16000 Гц, 16 бит, моно.")
                    return
                
                total_bytes_sent = 0
                chunk_count = 0
                
                while True:
                    audio_bytes = wf.readframes(CHUNK_SIZE)
                    if not audio_bytes:
                        break  # Файл закончился
                    
                    # Отправляем как бинарные данные
                    await self.websocket.send(audio_bytes)
                    total_bytes_sent += len(audio_bytes)
                    chunk_count += 1
                    
                    # Имитируем задержку реального времени
                    await asyncio.sleep(0.2)
                
                logger.info(f"✅ Аудио отправлено: {total_bytes_sent} байт, {chunk_count} чанков")
                
        except FileNotFoundError:
            logger.error(f"Аудиофайл для теста не найден: {audio_file}")
        except Exception as e:
            logger.error(f"Произошла ошибка при отправке аудио: {e}", exc_info=True)
    
    async def send_end(self):
        """Отправляет end сообщение для завершения сессии"""
        end_message = {
            "type": "end"
        }
        await self.websocket.send(json.dumps(end_message))
        logger.info("📨 Отправлено end сообщение")
    
    async def receive_messages(self):
        """Принимает сообщения от backend (идеи)"""
        try:
            async for message in self.websocket:
                try:
                    data = json.loads(message)
                    msg_type = data.get("type")
                    
                    if msg_type == "idea":
                        logger.info("💡 Получена идея от backend:")
                        logger.info(f"   ClientId: {data.get('clientId')}")
                        logger.info(f"   EventId: {data.get('eventId')}")
                        
                        idea = data.get('idea', {})
                        logger.info(f"   Идея ID: {idea.get('id')}")
                        logger.info(f"   Название: {idea.get('title')}")
                        logger.info(f"   Описание: {idea.get('description')}")
                        logger.info("-" * 80)
                        
                        self.received_ideas.append(data)
                    else:
                        logger.debug(f"Получено сообщение от backend: {data}")
                        
                except json.JSONDecodeError:
                    logger.warning("Получено не-JSON сообщение от backend")
                except Exception as e:
                    logger.error(f"Ошибка обработки сообщения от backend: {e}")
                    
        except websockets.exceptions.ConnectionClosed:
            logger.info("Соединение с backend закрыто")
        except asyncio.CancelledError:
            logger.debug("Задача приёма сообщений отменена")


async def run_test():
    """Запускает тестового клиента"""
    
    client = BackendClient()
    
    logger.info("🚀 Запуск тестового клиента backend")
    logger.info("💡 Убедитесь, что:")
    logger.info("   - Spring Boot backend запущен на ws://localhost:8080/ws/client")
    logger.info("   - STT сервис запущен и доступен для backend")
    logger.info("   - Redis запущен")
    logger.info("=" * 80)
    
    # Подключаемся к backend и отправляем аудио
    await client.connect_and_test(AUDIO_FILE)
    
    # Показываем итоги
    logger.info("=" * 80)
    logger.info(f"📊 Тест завершён. Получено идей: {len(client.received_ideas)}")
    
    if client.received_ideas:
        logger.info("\n📝 Все полученные идеи:")
        for idx, result in enumerate(client.received_ideas, 1):
            idea = result.get('idea', {})
            logger.info(f"\n  💡 Идея #{idx}:")
            logger.info(f"    ID: {idea.get('id')}")
            logger.info(f"    Название: {idea.get('title')}")
            logger.info(f"    Описание: {idea.get('description')}")
    else:
        logger.warning("⚠️  Идеи не получены. Проверьте:")
        logger.warning("   - Логи Spring Boot backend")
        logger.warning("   - Логи STT сервиса")
        logger.warning("   - Подключение к IdeaExtractor (GigaChat)")
    
    logger.info("=" * 80)


if __name__ == "__main__":
    try:
        asyncio.run(run_test())
    except KeyboardInterrupt:
        logger.info("\n👋 Тестовый клиент остановлен")