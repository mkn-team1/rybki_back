import logging

logger = logging.getLogger(__name__)

class MockBackendSender:
    """
    Debug-версия BackendSender для отладки.
    Просто выводит полученные данные в консоль вместо отправки на реальный бэкенд.
    """
    def __init__(self, backend_url: str = None):
        self.text = ""
        logger.info("MockBackendSender initialized (debug mode - console output only)")

    async def send(self, payload: dict):
        """
        Выводит полученное сообщение в консоль.
        """
        logger.info("📤 [DEBUG BACKEND] Received payload: %s", payload)
        self.text += " " + payload.get("text", "")
        logger.info("📤 [DEBUG BACKEND] Updated text: %s", self.text)