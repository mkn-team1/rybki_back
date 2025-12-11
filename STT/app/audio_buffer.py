import logging
from config import (
    SAMPLE_RATE, MIN_SEND_S, PREFERRED_SEND_S
)

logger = logging.getLogger(__name__)

def seconds_to_bytes(s: float) -> int:
    return int(s * SAMPLE_RATE * 2)  # PCM16 mono

class AudioBufferManager:
    def __init__(self):
        self.buffer = bytearray()
        self.last_speech_detected = False

    def append(self, chunk: bytes):
        self.buffer.extend(chunk)
        logger.debug("Buffer append: +%d bytes, total: %d bytes", len(chunk), len(self.buffer))

    def should_transcribe(self) -> bool:
        blen = len(self.buffer)
        blen_sec = blen / (SAMPLE_RATE * 2)
        
        if blen < seconds_to_bytes(MIN_SEND_S):
            return False

        if blen >= seconds_to_bytes(PREFERRED_SEND_S):
            logger.debug("Speech + preferred length reached (%.2fs >= %.2fs) -> transcribe",
                        blen_sec, PREFERRED_SEND_S)
            return True

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
