import asyncio
import websockets
import wave
import logging
import os
import json

# Настройка логирования
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

WEBSOCKET_URI = "ws://localhost:8001/ws/stt"
AUDIO_FILE = os.path.join(os.path.dirname(__file__), "out.wav")
SAMPLE_RATE = 16000
CHUNK_SIZE = int(SAMPLE_RATE * 0.1) # Отправляем по 100 мс аудио

async def run_speed_test():
    """
    Подключается к STT сервису, отправляет аудиофайл и замеряет время.
    """
    try:
        with wave.open(AUDIO_FILE, 'rb') as wf:
            if wf.getframerate() != SAMPLE_RATE or wf.getsampwidth() != 2 or wf.getnchannels() != 1:
                logger.error(f"Неверный формат аудиофайла: {AUDIO_FILE}. "
                             f"Требуется WAV, 16000 Гц, 16 бит, моно.")
                return
            
            logger.info(f"Подключение к {WEBSOCKET_URI}...")
            async with websockets.connect(WEBSOCKET_URI) as websocket:
                logger.info("Соединение установлено. Отправка метаданных...")
                
                # Отправляем метаданные для начала сессии
                start_message = {
                    "type": "start",
                    "clientId": "test_client_123",
                    "eventId": "test_event_456"
                }
                await websocket.send(json.dumps(start_message))
                
                logger.info("Начало отправки аудиопотока...")
                total_bytes_sent = 0
                
                while True:
                    audio_bytes = wf.readframes(CHUNK_SIZE)
                    if not audio_bytes:
                        break # Файл закончился
                    
                    await websocket.send(audio_bytes)
                    total_bytes_sent += len(audio_bytes)
                    
                    # Имитируем задержку реального времени
                    await asyncio.sleep(0.1) 
                
                logger.info(f"Аудиопоток отправлен ({total_bytes_sent} байт). "
                            f"Отправка сообщения о завершении...")

                # Отправляем сообщение о завершении
                end_message = {"type": "end"}
                await websocket.send(json.dumps(end_message))
                
                logger.info("Тест завершен. Ожидание закрытия соединения сервером...")

    except FileNotFoundError:
        logger.error(f"Аудиофайл для теста не найден: {AUDIO_FILE}")
    except ConnectionRefusedError:
        logger.error(f"Не удалось подключиться к {WEBSOCKET_URI}. "
                     f"Убедитесь, что сервер запущен.")
    except Exception as e:
        logger.error(f"Произошла ошибка: {e}")

if __name__ == "__main__":
    asyncio.run(run_speed_test())