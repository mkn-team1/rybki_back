import asyncio
import json
import logging
from typing import Optional

import websockets.legacy.client

logger = logging.getLogger(__name__)

class BackendSender:
    def __init__(self, backend_url: str):
        self.url = backend_url
        self.ws: Optional[websockets.legacy.client.WebSocketClientProtocol] = None

    async def start(self):
        """Запуск фонового reconnect-loop после старта event loop"""
        asyncio.create_task(self._reconnect_loop())

    async def _reconnect_loop(self):
        while True:
            try:
                logger.info("Connecting to backend WS at %s", self.url)
                self.ws = await websockets.connect(self.url)
                logger.info("Successfully connected to backend WS")

                await self.ws.wait_closed()
                logger.info("Backend WS connection closed")
                
            except Exception as e:
                logger.warning("Backend WS connection failed: %s", e)
            
            await asyncio.sleep(2)

    async def send(self, payload: dict):
        if self.ws and not self.ws.closed:
            try:
                await self.ws.send(json.dumps(payload, ensure_ascii=False))
                logger.info("Successfully sent to backend: %s", payload)
            except Exception as e:
                logger.error("Failed to send to backend WS: %s", e)
        else:
            logger.warning("Backend WS not connected. Payload not sent: %s", payload)