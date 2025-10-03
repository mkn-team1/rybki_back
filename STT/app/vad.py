import torch
import numpy as np
import logging
from typing import Optional
from config import SAMPLE_RATE

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

    def has_speech(self, audio: np.ndarray, sample_rate: int = SAMPLE_RATE, threshold: float = 0.5) -> bool:
        
        audio_float32 = audio.astype(np.float32) / 32768.0
        
        tensor = torch.from_numpy(audio_float32).to(self.device)
        
        with torch.no_grad(): 
            prob = self.model(tensor, sample_rate=sample_rate).item()
        
        return prob > threshold