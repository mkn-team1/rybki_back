import json
import logging
import numpy as np
from fastapi import WebSocket, WebSocketDisconnect

from config import SAMPLE_RATE
from app.vad import SileroVAD
from app.lid import SpeechBrainLID
from app.vosk_model import VoskManager
from app.aggregator import TextAggregator
from app.backend_sender import BackendSender

logger = logging.getLogger(__name__)

class STTService:
    def __init__(self, backend_url: str):
        self.vad = SileroVAD()
        self.lid = SpeechBrainLID()
        self.vosk = VoskManager()
        self.backend_sender = BackendSender(backend_url)
        logger.info("STTService initialized")

    def _detect_lang(self, audio: np.ndarray) -> str:
        try:
            return self.lid.detect(audio)
        except Exception:
            return "en"

    async def handle_ws(self, websocket: WebSocket):
        await websocket.accept()
        session = str(id(websocket))
        logger.info("Client connected %s", session)

        client_meta = {"clientId": None, "eventId": None}
        pcm_buffer = bytearray()

        async def send_batch_to_backend(text_chunk, metadata):
            payload = {
                "type": "final_text",
                "clientId": metadata.get("clientId"),
                "eventId": metadata.get("eventId"),
                "text_chunk": text_chunk
            }
            await self.backend_sender.send(payload)

        aggregator = TextAggregator(send_batch_to_backend)

        try:
            while True:
                msg = await websocket.receive()

                if msg.get("type") == "websocket.receive" and "bytes" in msg:
                    chunk = msg["bytes"]
                    pcm_buffer.extend(chunk)

                    arr = np.frombuffer(pcm_buffer, dtype=np.int16)
                    if len(arr) < SAMPLE_RATE * 0.3:
                        continue

                    if not self.vad.has_speech(arr):
                        pcm_buffer = bytearray()
                        continue

                    lang = self._detect_lang(arr)
                    text = self.vosk.transcribe(arr, lang=lang)
                    pcm_buffer = bytearray()

                    await aggregator.add(text, client_meta)

                elif msg.get("type") == "websocket.receive" and "text" in msg:
                    obj = json.loads(msg["text"])
                    t = obj.get("type")
                    if t == "start":
                        client_meta["clientId"] = obj.get("clientId")
                        client_meta["eventId"] = obj.get("eventId")
                        logger.info("Session started client=%s event=%s",
                                    client_meta["clientId"],
                                    client_meta["eventId"])
                    elif t == "end":
                        await aggregator.flush(client_meta)
                        logger.info("Session ended client=%s event=%s",
                                    client_meta["clientId"],
                                    client_meta["eventId"])

        except WebSocketDisconnect:
            logger.info("Client disconnected %s", session)
        finally:
            await aggregator.flush(client_meta)
