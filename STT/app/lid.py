import numpy as np
import logging
import torch
import os
from speechbrain.inference.speaker import EncoderClassifier
from config import SAMPLE_RATE, BASE_DIR

logger = logging.getLogger(__name__)

class SpeechBrainLID:
    def __init__(self):
        logger.info("Loading SpeechBrain LID (may take time)...")
        self.classifier = EncoderClassifier.from_hparams(
            source="speechbrain/lang-id-voxlingua107-ecapa",
            savedir=os.path.join(BASE_DIR, "pretrained_models", "lang-id")
        )
        self.classifier.hparams.label_encoder.ignore_len()
        logger.info("SpeechBrain LID loaded")

    def detect(self, pcm16: np.ndarray, sr: int = SAMPLE_RATE) -> str:
        try:
            waveform = torch.from_numpy(pcm16.astype(np.float32) / 32768.0).unsqueeze(0)
            
            prediction = self.classifier.classify_batch(waveform)
            text_lab = prediction[3]
            
            lang = text_lab[0]
            
            return self._normalize_lang_code(str(lang))
        except Exception as e:
            logger.error(f"Language detection failed: {e}", exc_info=True)
            return "en" 
        
    def _normalize_lang_code(self, lang: str) -> str:
        lang = lang.lower().split(':')[0].strip()
        
        # Supported languages mapping
        if lang in ['ru', 'rus']:
            return 'ru'
        elif lang in ['en', 'eng']:
            return 'en'
        else:
            logger.debug(f"Unsupported language detected: {lang}, falling back to 'en'")
            return 'en'
    