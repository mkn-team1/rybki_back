import logging
from config import CHAR_THRESHOLD

logger = logging.getLogger(__name__)

class TextAggregator:
    def __init__(self, send_callback, max_chars: int = CHAR_THRESHOLD):
        self.buffer = []
        self.send_callback = send_callback
        self.max_chars = max_chars

    async def add(self, text: str, metadata: dict):
        self.buffer.append(text)
        total = " ".join(self.buffer)
        if len(total) >= self.max_chars:
            await self.flush(metadata)

    async def flush(self, metadata: dict):
        if not self.buffer:
            return
        total = " ".join(self.buffer)
        self.buffer = []
        await self.send_callback(total, metadata)


