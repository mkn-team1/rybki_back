import uvicorn
import logging
from fastapi import FastAPI, WebSocket
from fastapi.middleware.cors import CORSMiddleware
from app.stt_service import STTService
from app.backend_sender import BackendSender
from config import BACKEND_WS_URL

logging.basicConfig(
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
    level=logging.INFO
)

app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=['*'],
    allow_credentials=True,
    allow_methods=['*'],
    allow_headers=['*'],
)


stt_service = STTService(BackendSender(BACKEND_WS_URL))

@app.websocket("/ws/stt")
async def websocket_endpoint(websocket: WebSocket):
    await stt_service.handle_ws(websocket)


if __name__ == "__main__":
    uvicorn.run(
        app,
        host="localhost",
        port=8001,
        ws_ping_interval=20,
        ws_ping_timeout=20,
        timeout_keep_alive=5
    )