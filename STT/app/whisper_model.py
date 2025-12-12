from mimetypes import init
import logging, time
import numpy as np
import torch
from faster_whisper import WhisperModel
from config import WHISPER_MODEL, BEAM_SIZE, COMPUTE_TYPE, DEVICE, THREADS

logger = logging.getLogger(__name__)

initial_prompt_for_whisper="Стенограмма рабочей встречи. Обсуждение планов, задач и сроков. Привет всем, давайте начнем."

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
        logger.info("Whisper model loaded")

    def transcribe(self, pcm16_bytes: bytes, beam_size: int = BEAM_SIZE):
        start_time = time.time()
        
        arr = np.frombuffer(pcm16_bytes, dtype=np.int16).astype("float32") / 32768.0
        audio_duration = len(arr) / 16000.0
        logger.debug("Whisper transcribing %.2fs of audio (beam_size=%d)...", audio_duration, beam_size)

        segments, info = self.model.transcribe(
            arr,
            task="transcribe",
            language="ru",
            
            beam_size=beam_size,

            best_of=None,
            temperature=0.0,
            patience=1.0,

            vad_filter=True,
            vad_parameters=dict(
                min_silence_duration_ms=500,
                threshold=0.4,
                speech_pad_ms=300
            ),

            no_speech_threshold=0.5,
            logprob_threshold=-0.9,

            initial_prompt=initial_prompt_for_whisper,

            condition_on_previous_text=False,
            compression_ratio_threshold=2.2,
            no_repeat_ngram_size=2,
            word_timestamps=False,
        )
        segs = list(segments)
        text = " ".join(s.text for s in segs).strip()
        
        elapsed = time.time() - start_time
        logger.debug("✅ Whisper done in %.2fs (RTF: %.2fx): '%s'",
                   elapsed, elapsed / audio_duration if audio_duration > 0 else 0, text)
        
        return {"text": text, "segments": segs}
