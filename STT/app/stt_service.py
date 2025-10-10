import json, logging
from fastapi import WebSocket, WebSocketDisconnect
from app.vad import SileroVAD
from app.vosk_model import VoskManager
from app.whisper_model import WhisperManager
from app.aggregator import TextAggregator
from app.audio_buffer import AudioBufferManager
from config import MODEL

logger = logging.getLogger(__name__)

class STTService:
    def __init__(self, backend=None):
        self.vad = SileroVAD()
        self.model = VoskManager() if MODEL == "Vosk" else WhisperManager()
        self.backend = backend

    async def handle_ws(self, websocket: WebSocket):
        await websocket.accept()
        session = str(id(websocket))
        logger.debug("client connected %s", session)

        client_meta = {"clientId": None, "eventId": None}
        audio_buffer = AudioBufferManager(self.vad)

        async def send_batch(text_chunk, metadata):
            payload = {
                "type": "final_text",
                "clientId": metadata.get("clientId"),
                "eventId": metadata.get("eventId"),
                "text": text_chunk,
            }
            await self.backend.send(payload)

        aggregator = TextAggregator(send_batch)

        try:
            while True:
                try:
                    msg = await websocket.receive()

                    if msg.get("type") == "websocket.disconnect":
                        logger.debug("client disconnected %s (disconnect message)", session)
                        break
                except WebSocketDisconnect:
                    logger.debug("client disconnected %s", session)
                    break
                except RuntimeError as e:
                    logger.debug("websocket receive runtime error (treat as disconnect): %s", e)
                    break

                if msg.get("type") == "websocket.receive" and "bytes" in msg:
                    chunk = msg["bytes"]
                    audio_buffer.append(chunk)

                    if audio_buffer.should_transcribe():
                        pcm_data = audio_buffer.pop_chunk()
                        res = self.model.transcribe(pcm_data)
                        text = res.get("text", "").strip()
                        if text:
                            await aggregator.add(text, client_meta)

                elif msg.get("type") == "websocket.receive" and "text" in msg:
                    obj = json.loads(msg["text"])
                    if obj.get("type") == "start":
                        client_meta["clientId"] = obj.get("clientId")
                        client_meta["eventId"] = obj.get("eventId")
                        logger.debug("session start %s/%s", client_meta["clientId"], client_meta["eventId"])
                    elif obj.get("type") == "end":
                        await aggregator.flush(client_meta)
                        logger.debug("session end %s/%s", client_meta["clientId"], client_meta["eventId"])

        except Exception as e:
            logger.error("Error in session %s: %s", session, e)
        finally:
            pcm_data = audio_buffer.pop_chunk()
            res = self.model.transcribe(pcm_data)
            text = res.get("text", "").strip()
            if text:
                await aggregator.add(text, client_meta)
            await aggregator.flush(client_meta)

