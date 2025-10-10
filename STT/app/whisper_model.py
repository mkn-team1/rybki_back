import logging, time
import numpy as np
import torch
from faster_whisper import WhisperModel
from app.lid import SpeechBrainLID
from config import CHUNK_SIZE_S, WHISPER_MODEL, BEAM_SIZE, COMPUTE_TYPE, DEVICE, THREADS

logger = logging.getLogger(__name__)

def pick_device():
    if DEVICE:
        return DEVICE
    return "cuda" if torch.cuda.is_available() else "cpu"

class WhisperManager:
    def __init__(self):
        device = pick_device()
        compute_type = COMPUTE_TYPE
        if compute_type is None:
            compute_type = "float16" if device == "cuda" else "int8"

        logger.info("Loading Whisper model %s on %s (compute_type=%s)...",
                    WHISPER_MODEL, device, compute_type)
        self.model = WhisperModel(
            WHISPER_MODEL,
            device=device,
            compute_type=compute_type,
            cpu_threads=THREADS
        )
        self.LID = SpeechBrainLID()
        logger.info("Whisper model loaded")

    def transcribe(self, pcm16_bytes: bytes, beam_size: int = BEAM_SIZE):
        start_time = time.time()
        
        arr = np.frombuffer(pcm16_bytes, dtype=np.int16).astype("float32") / 32768.0
        audio_duration = len(arr) / 16000.0
        logger.debug("Whisper transcribing %.2fs of audio (beam_size=%d)...", audio_duration, beam_size)

        lang = self.LID.detect(np.frombuffer(pcm16_bytes, dtype=np.int16))

        segments, info = self.model.transcribe(
            arr,
            task="transcribe",
            beam_size=beam_size,
            language=lang,
            vad_filter=False,
            condition_on_previous_text=False,
            temperature=0.0,
            chunk_length=CHUNK_SIZE_S
        )
        segs = list(segments)
        text = " ".join(s.text for s in segs).strip()
        
        elapsed = time.time() - start_time
        logger.debug("✅ Whisper done in %.2fs (RTF: %.2fx): '%s'",
                   elapsed, elapsed / audio_duration if audio_duration > 0 else 0, text)
        
        return {"text": text, "language": lang, "segments": segs}
