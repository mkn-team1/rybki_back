import asyncio
import json
import base64
import logging
import websockets.legacy.client
from typing import Optional
from app.audio_buffer import AudioBufferManager
from app.aggregator import TextAggregator
from app.vad import SileroVAD
from app.vosk_model import VoskManager
from app.whisper_model import WhisperManager
from config import MODEL, BACKEND_WS_URL

logger = logging.getLogger(__name__)


class STTService:
    def __init__(self):
        self.vad = SileroVAD()
        self.model = WhisperManager() if MODEL == "Whisper" else VoskManager()
        self.sessions = {}  # key: (clientId, eventId)
        self.backend_ws: Optional[websockets.legacy.client.WebSocketClientProtocol] = None

    async def connect_to_backend(self):
        while True:
            try:
                logger.info(f"Connecting to backend WS at {BACKEND_WS_URL}")
                async with websockets.connect(BACKEND_WS_URL) as ws:
                    self.backend_ws = ws
                    logger.info("Connected to backend successfully")

                    await self.listen_loop(ws)
            except Exception as e:
                logger.error(f"Lost connection to backend: {e}, reconnecting in 3s")
                await asyncio.sleep(3)

    async def listen_loop(self, ws):
        '''
        message = {
            "clientId": "...",
            "eventId": "...",
            "audio": "<base64 PCM16 chunk>"
        }
        '''
        async for message in ws:
            try:
                data = json.loads(message)
                msg_type = data.get("type", "audio")
                client_id = data.get("clientId")
                event_id = data.get("eventId")

                if not (client_id and event_id):
                    logger.warning("Received message without clientId/eventId: %s", data)
                    continue

                key = (client_id, event_id)

                if msg_type == "audio":
                    audio_b64 = data.get("audio")
                    if not audio_b64:
                        logger.warning("Audio message missing 'audio' field")
                        continue

                    audio_bytes = base64.b64decode(audio_b64)

                    if key not in self.sessions:
                        self.sessions[key] = {
                            "audio_buffer": AudioBufferManager(self.vad),
                            "aggregator": TextAggregator(lambda text, meta: self.send_to_backend(text, meta)),
                            "meta": {"clientId": client_id, "eventId": event_id}
                        }

                    session = self.sessions[key]
                    buf: AudioBufferManager = session["audio_buffer"]
                    agg: TextAggregator = session["aggregator"]

                    buf.append(audio_bytes)
                    if buf.should_transcribe():
                        pcm = buf.pop_chunk()
                        result = self.model.transcribe(pcm)
                        text = result.get("text", "").strip()
                        if text:
                            await agg.add(text, session["meta"])

                elif msg_type in ("disconnect", "end"):
                    logger.debug(f"Client {client_id}/{event_id} disconnected -> cleaning up")
                    await self._cleanup_session(key)

            except Exception as e:
                logger.exception("Error processing WS message: %s", e)


    async def _cleanup_session(self, key):
        session = self.sessions.pop(key, None)
        if not session:
            return
        try:
            buf: AudioBufferManager = session["audio_buffer"]
            agg: TextAggregator = session["aggregator"]
            pcm = buf.pop_chunk()
            if pcm:
                res = self.model.transcribe(pcm)
                text = res.get("text", "").strip()
                if text:
                    await agg.add(text, session["meta"])
            await agg.flush(session["meta"])
            logger.debug("Session %s cleaned up successfully", key)
        except Exception as e:
            logger.error("Error during cleanup of %s: %s", key, e)


    async def send_to_backend(self, text: str, metadata: dict):
        payload = {
            "type": "final_text",
            "clientId": metadata["clientId"],
            "eventId": metadata["eventId"],
            "text": text,
        }
        if not self.backend_ws:
            logger.warning("Backend WS not connected, skipping send")
            return

        try:
            await self.backend_ws.send(json.dumps(payload, ensure_ascii=False))
            logger.debug("Sent transcription to backend: %s", payload)
        except AttributeError:
            logger.error("Backend WS object doesn't support send(), dropping connection reference")
            self.backend_ws = None
        except Exception as e:
            logger.error("Failed to send to backend: %s", e)
            self.backend_ws = None
