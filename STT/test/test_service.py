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
STT_WS_URI = "ws://localhost:8001"  # STT сервис подключится сюда как клиент
AUDIO_FILE = os.path.join(os.path.dirname(__file__), "out.wav")
SAMPLE_RATE = 16000
CHUNK_SIZE = int(SAMPLE_RATE * 0.2)  # Отправляем по 200 мс аудио

class MockBackend:
    """Мок-бэкенд, который форвардит аудио в STT и получает результаты"""
    
    def __init__(self):
        self.stt_connection = None
        self.received_results = []
    
    async def handle_stt_connection(self, websocket):
        """Обработчик подключения от STT сервиса"""
        logger.info("✅ STT сервис подключился к мок-бэкенду")
        self.stt_connection = websocket
        
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
            logger.info("STT сервис отключился")
        finally:
            self.stt_connection = None
    
    async def send_audio_to_stt(self, client_id: str, event_id: str, audio_file: str):
        """Отправляет аудио в STT через WebSocket"""
        
        # Ждём подключения STT
        logger.info("⏳ Ожидание подключения STT сервиса...")
        for _ in range(30):  # Ждём до 30 секунд
            if self.stt_connection:
                break
            await asyncio.sleep(1)
        
        if not self.stt_connection:
            logger.error("❌ STT сервис не подключился за 30 секунд")
            return
        
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
    """Запускает мок-бэкенд и отправляет тестовое аудио"""
    
    backend = MockBackend()
    
    # Запускаем WebSocket сервер для приёма подключения от STT
    logger.info("🚀 Запуск мок-бэкенда на ws://localhost:8080/stt-ingest")
    
    async with websockets.serve(backend.handle_stt_connection, "localhost", 8080):
        logger.info("✅ Мок-бэкенд запущен и ожидает подключения STT сервиса")
        logger.info("💡 Убедитесь, что STT сервис запущен и подключается к ws://localhost:8080/stt-ingest")
        logger.info("-" * 60)
        
        # Отправляем тестовое аудио
        await backend.send_audio_to_stt(
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
        logger.info("🛑 Нажмите Ctrl+C для остановки мок-бэкенда")
        
        # Держим сервер запущенным
        await asyncio.Future()  # Бесконечное ожидание


if __name__ == "__main__":
    try:
        asyncio.run(run_test())
    except KeyboardInterrupt:
        logger.info("\n👋 Мок-бэкенд остановлен")