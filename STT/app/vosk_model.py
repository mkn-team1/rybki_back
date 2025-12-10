import os
import json
import logging
import numpy as np
import vosk
from app.lid import SpeechBrainLID
from config import VOSK_MODEL_RU, VOSK_MODEL_EN, SAMPLE_RATE

logger = logging.getLogger(__name__)


class VoskManager:
    def __init__(self, ru_path=VOSK_MODEL_RU, en_path=VOSK_MODEL_EN, sr=SAMPLE_RATE):

        logger.info("Loading Vosk models...")
        if not os.path.exists(ru_path) or not os.path.exists(en_path):
            logger.error("Vosk models not found. ru=%s en=%s", ru_path, en_path)
            raise RuntimeError("Vosk models missing")
        
        models = {
            "ru": vosk.Model(ru_path),
            "en": vosk.Model(en_path)
        }
        
        self.recognizers = {
            "ru": vosk.KaldiRecognizer(models["ru"], sr),
            "en": vosk.KaldiRecognizer(models["en"], sr)
        }
        
        self.sr = sr
        self.LID = SpeechBrainLID()
        logger.info("Vosk models and recognizers loaded")
    
    def transcribe(self, audio: bytes) -> str:
        
        logger.debug("Vosk transcribe called")
        arr = np.frombuffer(audio, dtype=np.int16)
        lang = self.LID.detect(arr)

        rec = self.recognizers.get(lang, self.recognizers["en"])
        
        rec.AcceptWaveform(arr.tobytes())
        result = rec.FinalResult()
        logger.debug("Vosk transcription result: %s", result)
        return json.loads(result)