import asyncio
import json
import base64
import logging
from typing import Optional
from fastapi import WebSocket, WebSocketDisconnect
from app.audio_buffer import AudioBufferManager
from app.aggregator import TextAggregator
from app.vad import SileroVAD
from app.vosk_model import VoskManager
from app.whisper_model import WhisperManager
from config import MODEL

logger = logging.getLogger(__name__)


class STTService:
    def __init__(self):
        self.vad = SileroVAD()
        self.model = WhisperManager() if MODEL == "Whisper" else VoskManager()
        self.sessions = {}  # key: (clientId, eventId)
        self.backend_ws: Optional[WebSocket] = None
        self._lock = asyncio.Lock()

    async def handle_backend_connection(self, websocket: WebSocket):
        async with self._lock:
            if self.backend_ws is not None:
                logger.warning("Backend already connected, rejecting new connection")
                await websocket.close(code=1008, reason="Only one backend connection allowed")
                return
            
            await websocket.accept()
            self.backend_ws = websocket
            logger.info("Backend connected successfully")

        try:
            await self.listen_loop(websocket)
        except WebSocketDisconnect:
            logger.info("Backend disconnected")
        except Exception as e:
            logger.error(f"Error in backend connection: {e}")
        finally:
            async with self._lock:
                if self.backend_ws == websocket:
                    self.backend_ws = None
                    logger.info("Backend connection closed")
            await self._cleanup_all_sessions()

    async def listen_loop(self, websocket: WebSocket):
        '''
        message = {
            "clientId": "...",
            "eventId": "...",
            "audio": "<base64 PCM16 chunk>"
        }
        '''
        while True:
            try:
                message = await websocket.receive_text()
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

            except WebSocketDisconnect:
                raise
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

    async def _cleanup_all_sessions(self):
        logger.info(f"Cleaning up all {len(self.sessions)} active sessions")
        keys = list(self.sessions.keys())
        for key in keys:
            await self._cleanup_session(key)

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
            await self.backend_ws.send_text(json.dumps(payload, ensure_ascii=False))
            logger.debug("Sent transcription to backend: %s", payload)
        except Exception as e:
            logger.error("Failed to send to backend: %s", e)
            async with self._lock:
                if self.backend_ws:
                    self.backend_ws = None
