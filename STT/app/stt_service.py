import logging
import time
import json
import numpy as np
from fastapi import WebSocket, WebSocketDisconnect

from .vosk_model import VoskManager
from .lid import SpeechBrainLID
from .vad import SileroVAD
from .backend_sender import BackendSender
from .aggregator import SmartAggregator

logger = logging.getLogger(__name__)

class STTService:
    def __init__(self, backend_sender: BackendSender):
        self.vosk = VoskManager()
        self.lid =  SpeechBrainLID()
        self.vad = SileroVAD()
        self.backend_sender = backend_sender
        logger.info("STTService initialized")

    async def _send_final_text(self, text_chunk: str, metadata: dict):
        if not text_chunk.strip():
            return
        payload = {
            "type": "final_text",
            "clientId": metadata.get("clientId"),
            "eventId": metadata.get("eventId"),
            "text_chunk": text_chunk
        }
        await self.backend_sender.send(payload)

    async def handle_ws(self, websocket: WebSocket):
        session_id = f"session_{time.time()}"
        logger.info(f"New client connected. Session: {session_id}")
        await websocket.accept()

        client_meta = {"clientId": None, "eventId": None}
        aggregator = SmartAggregator(self._send_final_text)
        
        pcm_buffer = bytearray()
        is_speaking = False
        silence_start_time = None
        MIN_SILENCE_DURATION_MS = 700

        try:
            while True:
                msg = await websocket.receive()

                if "bytes" in msg:
                    chunk = msg["bytes"]
                    pcm_buffer.extend(chunk)
                    
                    speech_detected = self.vad.has_speech(np.frombuffer(chunk, dtype=np.int16))

                    if speech_detected:
                        is_speaking = True
                        silence_start_time = None
                    
                    if not speech_detected and is_speaking:
                        if silence_start_time is None:
                            silence_start_time = time.time()
                        
                        if (time.time() - silence_start_time) * 1000 > MIN_SILENCE_DURATION_MS:
                            logger.info(f"[{session_id}] End of phrase detected by VAD.")
                            
                            audio_data = np.frombuffer(pcm_buffer, dtype=np.int16)
                            lang = self.lid.detect(audio_data)
                            text = self.vosk.transcribe(audio_data, lang)
                            
                            logger.info(f"[{session_id}] Recognized: lang='{lang}', text='{text}'")
                            
                            await aggregator.add(text, lang, client_meta, is_final=True)
                            
                            pcm_buffer = bytearray()
                            is_speaking = False
                            silence_start_time = None

                elif "text" in msg:
                    obj = json.loads(msg["text"])
                    msg_type = obj.get("type")

                    if msg_type == "start":
                        client_meta["clientId"] = obj.get("clientId")
                        client_meta["eventId"] = obj.get("eventId")
                        logger.info(f"[{session_id}] Session started client={client_meta['clientId']} event={client_meta['eventId']}")
                    
                    elif msg_type == "end":
                        logger.info(f"[{session_id}] 'end' message received.")
                        if pcm_buffer:
                            audio_data = np.frombuffer(pcm_buffer, dtype=np.int16)
                            lang = self.lid.detect(audio_data)
                            text = self.vosk.transcribe(audio_data, lang)
                            await aggregator.add(text, lang, client_meta, is_final=True)
                        
                        await aggregator.flush_all(client_meta)
                        logger.info(f"[{session_id}] Session ended for client={client_meta['clientId']}")
                        break

        except WebSocketDisconnect:
            logger.info(f"[{session_id}] Client disconnected.")
        finally:
            await aggregator.flush_all(client_meta)
            logger.info(f"[{session_id}] Cleanup complete.")
