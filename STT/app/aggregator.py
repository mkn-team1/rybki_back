import asyncio, time, logging

from config import CHAR_THRESHOLD, TIME_THRESHOLD
logger = logging.getLogger(__name__)

class TextAggregator:
    def __init__(self, send_cb, char_threshold=CHAR_THRESHOLD, time_threshold=TIME_THRESHOLD):
        self.buffer = []
        self.send_cb = send_cb
        self.char_threshold = char_threshold
        self.time_threshold = time_threshold
        self.last_send = time.time()

    async def add(self, text: str, metadata: dict):
        if text:
            self.buffer.append(text)
            logger.debug("Aggregator: added text '%s' (buffer count: %d)", text, len(self.buffer))
        now = time.time()
        total = sum(len(s) for s in self.buffer)
        time_since_last = now - self.last_send
        
        if total >= self.char_threshold:
            logger.debug("Char threshold reached (%d >= %d) -> flushing", total, self.char_threshold)
            await self.flush(metadata)
        elif time_since_last >= self.time_threshold:
            logger.debug("Time threshold reached (%.1fs >= %.1fs) -> flushing",
                        time_since_last, self.time_threshold)
            await self.flush(metadata)

    async def flush(self, metadata: dict):
        if not self.buffer:
            logger.debug("Flush called but buffer is empty")
            self.last_send = time.time()
            return
        chunk = "\n".join(self.buffer)
        logger.debug("Flushing %d text segments: '%s'", len(self.buffer), chunk)
        self.buffer = []
        self.last_send = time.time()
        
        logger.debug("Sending to backend...")
        await self.send_cb(chunk, metadata)
        logger.debug("Sent to backend successfully")
