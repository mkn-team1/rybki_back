import asyncio
import websockets
import wave
import logging
import os
import json
import base64

# Настройка логирования
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# Конфигурация
STT_WS_URI = "ws://localhost:8001/stt-ingest"  # Backend подключается к STT сервису
AUDIO_FILE = os.path.join(os.path.dirname(__file__), "out.wav")
SAMPLE_RATE = 16000
CHUNK_SIZE = int(SAMPLE_RATE * 0.2)  # Отправляем по 200 мс аудио

class MockBackend:
    """Мок-бэкенд, который подключается к STT и отправляет аудио"""
    
    def __init__(self):
        self.stt_connection = None
        self.received_results = []
    
    async def connect_and_send_audio(self, client_id: str, event_id: str, audio_file: str):
        """Подключается к STT сервису и отправляет аудио"""
        
        logger.info(f"🔌 Подключаемся к STT сервису: {STT_WS_URI}")
        
        try:
            async with websockets.connect(STT_WS_URI) as websocket:
                self.stt_connection = websocket
                logger.info("✅ Успешно подключились к STT сервису")
                
                # Запускаем задачу для приёма результатов
                receive_task = asyncio.create_task(self.receive_results(websocket))
                
                # Отправляем аудио
                await self.send_audio(websocket, client_id, event_id, audio_file)
                
                # Ждём немного для получения финальных результатов
                await asyncio.sleep(2)
                
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
            self.stt_connection = None
    
    async def receive_results(self, websocket):
        """Принимает результаты от STT сервиса"""
        try:
            async for message in websocket:
                try:
                    data = json.loads(message)
                    msg_type = data.get("type")
                    
                    if msg_type == "final_text":
                        logger.info("🎯 Получен результат от STT:")
                        logger.info(f"   ClientId: {data.get('clientId')}")
                        logger.info(f"   EventId: {data.get('eventId')}")
                        logger.info(f"   Text: {data.get('text')}")
                        logger.info("-" * 60)
                        self.received_results.append(data)
                    else:
                        logger.debug(f"Получено сообщение от STT: {data}")
                        
                except json.JSONDecodeError:
                    logger.warning("Получено не-JSON сообщение от STT")
                except Exception as e:
                    logger.error(f"Ошибка обработки сообщения от STT: {e}")
                    
        except websockets.exceptions.ConnectionClosed:
            logger.info("Соединение с STT закрыто")
        except asyncio.CancelledError:
            logger.debug("Задача приёма результатов отменена")
    
    async def send_audio(self, websocket, client_id: str, event_id: str, audio_file: str):
        """Отправляет аудио в STT через WebSocket"""
        
        logger.info("📤 Начинаем отправку аудио в STT...")
        
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
                    
                    # Кодируем в base64 и отправляем в формате JSON
                    audio_b64 = base64.b64encode(audio_bytes).decode('utf-8')
                    message = {
                        "type": "audio",
                        "clientId": client_id,
                        "eventId": event_id,
                        "audio": audio_b64
                    }
                    
                    await self.stt_connection.send(json.dumps(message))
                    total_bytes_sent += len(audio_bytes)
                    chunk_count += 1
                    
                    # Имитируем задержку реального времени
                    await asyncio.sleep(0.1)
                
                logger.info(f"✅ Аудио отправлено: {total_bytes_sent} байт, {chunk_count} чанков")
                
                # Отправляем сообщение о завершении
                end_message = {
                    "type": "end",
                    "clientId": client_id,
                    "eventId": event_id
                }
                await self.stt_connection.send(json.dumps(end_message))
                logger.info("📨 Отправлено сообщение о завершении сессии")
                
                # Ждём немного для получения финальных результатов
                await asyncio.sleep(2)
                
        except FileNotFoundError:
            logger.error(f"Аудиофайл для теста не найден: {audio_file}")
        except Exception as e:
            logger.error(f"Произошла ошибка при отправке аудио: {e}", exc_info=True)


async def run_test():
    """Запускает мок-бэкенд, подключается к STT и отправляет тестовое аудио"""
    
    backend = MockBackend()
    
    logger.info("🚀 Запуск тестового клиента")
    logger.info("💡 Убедитесь, что STT сервис запущен на ws://localhost:8001/stt-ingest")
    logger.info("-" * 60)
    
    # Подключаемся к STT и отправляем аудио
    await backend.connect_and_send_audio(
        client_id="test_client_123",
        event_id="test_event_456",
        audio_file=AUDIO_FILE
    )
    
    # Показываем итоги
    logger.info("=" * 60)
    logger.info(f"📊 Тест завершён. Получено результатов: {len(backend.received_results)}")
    
    if backend.received_results:
        logger.info("\n📝 Все полученные результаты:")
        for idx, result in enumerate(backend.received_results, 1):
            logger.info(f"\n  Результат #{idx}:")
            logger.info(f"    ClientId: {result.get('clientId')}")
            logger.info(f"    EventId: {result.get('eventId')}")
            logger.info(f"    Text: {result.get('text')}")
    else:
        logger.warning("⚠️  Результаты не получены. Проверьте логи STT сервиса.")
    
    logger.info("=" * 60)


if __name__ == "__main__":
    try:
        asyncio.run(run_test())
    except KeyboardInterrupt:
        logger.info("\n👋 Мок-бэкенд остановлен")