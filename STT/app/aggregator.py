import logging
from config import CHAR_THRESHOLD
from typing import Callable, Awaitable, Dict

logger = logging.getLogger(__name__)

class SmartAggregator:
    def __init__(self, send_callback: Callable[[str, Dict], Awaitable[None]], max_chars: int = CHAR_THRESHOLD):
        self.send_callback = send_callback
        self.max_chars = max_chars
        self.buffers: Dict[str, list[str]] = {}
        self.current_lang = None

    async def add(self, text: str, lang: str, metadata: dict, is_final: bool):
        if not text:
            return

        if self.current_lang and self.current_lang != lang:
            await self.flush(self.current_lang, metadata)

        self.current_lang = lang

        if lang not in self.buffers:
            self.buffers[lang] = []

        if is_final or not self.buffers[lang]:
             self.buffers[lang].append(text)
        else:
             self.buffers[lang][-1] = text 

        total = " ".join(self.buffers[lang])
        if len(total) >= self.max_chars:
            await self.flush(lang, metadata)

    async def flush(self, lang: str, metadata: dict):
        if lang not in self.buffers or not self.buffers[lang]:
            return

        text_to_send = " ".join(self.buffers[lang])
        logger.info(f"Flushing buffer for lang='{lang}': '{text_to_send}'")
        
        await self.send_callback(text_to_send, metadata)
        
        self.buffers[lang] = []
        
        if self.current_lang == lang:
            self.current_lang = None

    async def flush_all(self, metadata: dict):
        for lang in list(self.buffers.keys()):
            await self.flush(lang, metadata)
