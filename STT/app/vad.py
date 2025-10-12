import torch
import numpy as np
import logging
from typing import Optional
from config import SAMPLE_RATE, SILENCE_AFTER_S

logger = logging.getLogger(__name__)

class SileroVAD:
    def __init__(self, device: Optional[str] = None):
        self.device = device or ("cuda" if torch.cuda.is_available() else "cpu")
        logger.info("Loading Silero VAD on %s...", self.device)
        
        self.model, _ = torch.hub.load(
            repo_or_dir="snakers4/silero-vad",
            model="silero_vad",
            force_reload=False,
            verbose=False
        )
        self.model.to(self.device)
        self.model.eval()
        logger.info("Silero VAD loaded successfully")

    def has_speech(self, pcm16: bytes, sample_rate: int = SAMPLE_RATE, threshold: float = SILENCE_AFTER_S) -> bool:
        audio_float32 = np.frombuffer(pcm16, dtype=np.int16).astype(np.float32) / 32768.0
        tensor = torch.from_numpy(audio_float32).to(self.device)
        
        chunk_size = 512
        max_prob = 0.0
        
        with torch.no_grad():
            for i in range(0, len(tensor), chunk_size):
                chunk = tensor[i: i + chunk_size]
                
                if len(chunk) < chunk_size:
                    continue
                
                prob = self.model(chunk, sample_rate).item()
                max_prob = max(max_prob, prob)
                if prob > threshold:
                    logger.debug("VAD: speech detected (prob=%.3f > %.3f)", prob, threshold)
                    return True
        
        logger.debug("VAD: no speech (max_prob=%.3f <= %.3f)", max_prob, threshold)
        return False