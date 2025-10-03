import logging
from fastapi import FastAPI, WebSocket
from app.stt_service import STTService
from config import BACKEND_WS_URL

logging.basicConfig(level=logging.INFO)

app = FastAPI()
stt_service = STTService(BACKEND_WS_URL)

@app.websocket("/ws/stt")
async def websocket_endpoint(websocket: WebSocket):
    await stt_service.handle_ws(websocket)
