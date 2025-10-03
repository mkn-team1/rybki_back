import os
import json
import logging
import numpy as np
import vosk
from config import VOSK_MODEL_RU, VOSK_MODEL_EN, SAMPLE_RATE

logger = logging.getLogger(__name__)


class VoskManager:
    def __init__(self, ru_path=VOSK_MODEL_RU, en_path=VOSK_MODEL_EN, sr=SAMPLE_RATE):

        logger.info("Loading Vosk models...")
        if not os.path.exists(ru_path) or not os.path.exists(en_path):
            logger.error("Vosk models not found. ru=%s en=%s", ru_path, en_path)
            raise RuntimeError("Vosk models missing")
        
        self.models = {
            "ru": vosk.Model(ru_path),
            "en": vosk.Model(en_path)
        }
        self.sr = sr
        logger.info("Vosk models loaded")
    
    def transcribe(self, audio: np.ndarray, lang: str) -> str:
        model = self.models.get(lang, self.models["en"])
        rec = vosk.KaldiRecognizer(model, SAMPLE_RATE)
        rec.AcceptWaveform(audio.tobytes())
        result = rec.FinalResult()
        return json.loads(result).get("text", "")