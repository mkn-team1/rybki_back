import uvicorn
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI, WebSocket
from fastapi.middleware.cors import CORSMiddleware
from app.stt_service import STTService
from config import MODE

logging.basicConfig(
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
    level=(logging.INFO if MODE == "develop" else logging.DEBUG)
)

logger = logging.getLogger(__name__)

logger.info("🚀 Starting STT service - waiting for backend connection")

stt_service = STTService()

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("STT service ready to accept backend connection")
    yield
    logger.info("STT service shutting down")

app = FastAPI(lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=['*'],
    allow_credentials=True,
    allow_methods=['*'],
    allow_headers=['*'],
)

@app.websocket("/stt-ingest")
async def websocket_endpoint(websocket: WebSocket):
    """
    WebSocket endpoint для подключения backend.
    Принимает только одно подключение одновременно.
    """
    await stt_service.handle_backend_connection(websocket)

if __name__ == "__main__":
    uvicorn.run(
        app,
        host="localhost",
        port=8001,
        ws_ping_interval=20,
        ws_ping_timeout=20,
        timeout_keep_alive=5
    )