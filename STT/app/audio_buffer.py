import numpy as np
import logging
from config import (
    SAMPLE_RATE, MIN_SEND_S, PREFERRED_SEND_S, MAX_SEND_S
)

logger = logging.getLogger(__name__)

def seconds_to_bytes(s: float) -> int:
    return int(s * SAMPLE_RATE * 2)  # PCM16 mono

class AudioBufferManager:
    def __init__(self, vad):
        self.vad = vad
        self.buffer = bytearray()
        self.last_speech_detected = False

    def append(self, chunk: bytes):
        self.buffer.extend(chunk)
        logger.debug("Buffer append: +%d bytes, total: %d bytes", len(chunk), len(self.buffer))

    def _vad_speech_detected(self, pcm_bytes: bytes) -> bool:
        try:
            return self.vad.has_speech(pcm_bytes)
        except Exception as e:
            logger.warning("VAD failed: %s", e)
            return True

    def should_transcribe(self) -> bool:
        blen = len(self.buffer)
        blen_sec = blen / (SAMPLE_RATE * 2)
        
        if blen < seconds_to_bytes(MIN_SEND_S):
            return False

        last_segment = bytes(self.buffer[-seconds_to_bytes(0.6):])
        has_speech = self._vad_speech_detected(last_segment)

        if has_speech:
            if blen >= seconds_to_bytes(PREFERRED_SEND_S):
                logger.debug("Speech + preferred length reached (%.2fs >= %.2fs) -> transcribe",
                           blen_sec, PREFERRED_SEND_S)
                return True
        else:
            if self.last_speech_detected and blen >= seconds_to_bytes(MIN_SEND_S):
                logger.debug("End of utterance detected (silence after speech) -> transcribe")
                return True

        if blen >= seconds_to_bytes(MAX_SEND_S):
            logger.debug("Max length reached (%.2fs >= %.2fs) -> force transcribe",
                       blen_sec, MAX_SEND_S)
            return True

        self.last_speech_detected = has_speech
        return False

    def pop_chunk(self) -> bytes:
        total_len = len(self.buffer)
        if total_len == 0:
            logger.debug("pop_chunk: buffer empty")
            return b""
        send_data = bytes(self.buffer)
        self.buffer.clear()
        logger.debug("Popped chunk: %.2fs", len(send_data) / (SAMPLE_RATE * 2))
        return send_data
