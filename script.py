#!/usr/bin/env python3


######################################################################################################################
# Нужные зависимости: pip install websockets
#
# скрипт принимает WAV файл в нужном формате. Чтобы конвертировать аудио в нужный формат, можно использовать ffmpeg:
# ffmpeg -i input.mp3 -ar 16000 -ac 1 -sample_fmt s16 test_audio.wav
######################################################################################################################

"""
Тестовый клиент для отправки WAV файла на backend через WebSocket.
Отправляет аудио КАК БИНАРНЫЕ СООБЩЕНИЯ.
"""

import asyncio
import wave
import json
import logging
import uuid
from pathlib import Path

import websockets

# Настройка логирования
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Конфигурация
BACKEND_WS_URL = "ws://localhost:8080/ws/client"
AUDIO_FILE = "test_audio.wav"
CHUNK_DURATION_MS = 200
SAMPLE_RATE = 16000


def generate_ids():
    return {
        "clientId": f"test-client-{uuid.uuid4().hex[:8]}",
        "eventId": f"test-event-{uuid.uuid4().hex[:8]}"
    }


def read_wav_file(filepath: str):
    if not Path(filepath).exists():
        raise FileNotFoundError(f"Audio file not found: {filepath}")
    
    with wave.open(filepath, 'rb') as wf:
        channels = wf.getnchannels()
        sample_width = wf.getsampwidth()
        framerate = wf.getframerate()
        n_frames = wf.getnframes()
        
        logger.info(f"WAV file info:")
        logger.info(f"  Channels: {channels}")
        logger.info(f"  Sample width: {sample_width} bytes")
        logger.info(f"  Frame rate: {framerate} Hz")
        logger.info(f"  Total frames: {n_frames}")
        logger.info(f"  Duration: {n_frames / framerate:.2f} seconds")
        
        audio_data = wf.readframes(n_frames)
        duration = n_frames / framerate
        
        return audio_data, framerate, duration


def split_audio_chunks(audio_data: bytes, sample_rate: int, chunk_duration_ms: int):
    chunk_size_bytes = int(sample_rate * (chunk_duration_ms / 1000) * 2)
    
    chunks = []
    for i in range(0, len(audio_data), chunk_size_bytes):
        chunk = audio_data[i:i + chunk_size_bytes]
        chunks.append(chunk)
    
    logger.info(f"Split audio into {len(chunks)} chunks of ~{chunk_duration_ms}ms each")
    return chunks


async def send_audio_to_backend(audio_file: str):
    ids = generate_ids()
    logger.info(f"Generated IDs: {ids}")
    
    try:
        audio_data, sample_rate, duration = read_wav_file(audio_file)
    except Exception as e:
        logger.error(f"Failed to read audio file: {e}")
        return
    
    chunks = split_audio_chunks(audio_data, sample_rate, CHUNK_DURATION_MS)
    
    try:
        async with websockets.connect(BACKEND_WS_URL) as websocket:
            logger.info(f"Connected to {BACKEND_WS_URL}")
            
            # 1. Отправляем START как JSON (TEXT)
            start_message = {
                "type": "start",
                "clientId": ids["clientId"],
                "eventId": ids["eventId"],
                "sampleRate": sample_rate
            }
            await websocket.send(json.dumps(start_message))
            logger.info(f"Sent START message: {start_message}")
            
            await asyncio.sleep(0.5)
            
            # 2. Отправляем аудио чанки КАК БИНАРНЫЕ сообщения
            logger.info(f"Sending {len(chunks)} audio chunks as BINARY...")
            for idx, chunk in enumerate(chunks, 1):
                # Отправляем бинарные данные напрямую (не JSON, не base64)
                await websocket.send(chunk)
                
                logger.info(f"Sent binary chunk {idx}/{len(chunks)} ({len(chunk)} bytes)")
                
                # Небольшая задержка
                await asyncio.sleep(CHUNK_DURATION_MS / 1000 * 0.9)
                
                # Получаем ответы (если есть)
                try:
                    while True:
                        response = await asyncio.wait_for(websocket.recv(), timeout=0.01)
                        data = json.loads(response)
                        logger.info("=" * 60)
                        logger.info(f"RECEIVED: {json.dumps(data, indent=2, ensure_ascii=False)}")
                        logger.info("=" * 60)
                except asyncio.TimeoutError:
                    pass
            
            # 3. Отправляем END как JSON (TEXT)
            end_message = {
                "type": "end",
                "clientId": ids["clientId"],
                "eventId": ids["eventId"]
            }
            await websocket.send(json.dumps(end_message))
            logger.info(f"Sent END message: {end_message}")
            
            # 4. Ждём финальные ответы
            logger.info("Waiting for final responses...")
            try:
                while True:
                    response = await asyncio.wait_for(websocket.recv(), timeout=5.0)
                    data = json.loads(response)
                    logger.info("=" * 60)
                    logger.info(f"FINAL: {json.dumps(data, indent=2, ensure_ascii=False)}")
                    logger.info("=" * 60)
            except asyncio.TimeoutError:
                logger.info("No more messages (timeout)")
            except websockets.exceptions.ConnectionClosed:
                logger.info("Connection closed by server")
            
            logger.info("Test completed!")
            
    except websockets.exceptions.WebSocketException as e:
        logger.error(f"WebSocket error: {e}")
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
        import traceback
        traceback.print_exc()


def main():
    logger.info("Starting audio sender test client")
    logger.info(f"Target: {BACKEND_WS_URL}")
    logger.info(f"Audio file: {AUDIO_FILE}")
    logger.info(f"Chunk duration: {CHUNK_DURATION_MS}ms")
    
    asyncio.run(send_audio_to_backend(AUDIO_FILE))


if __name__ == "__main__":
    main()
