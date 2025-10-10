import uvicorn
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI, WebSocket
from fastapi.middleware.cors import CORSMiddleware
from app.stt_service import STTService
from app.backend_sender import BackendSender
from test.mock_backend import MockBackendSender
from config import MODE, BACKEND_WS_URL

logging.basicConfig(
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
    level= (logging.INFO if MODE == "develop" else logging.DEBUG)
)

logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    if MODE != "debug":
        await backend.start()
        logger.info("Backend reconnect loop started")
    yield

app = FastAPI(lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=['*'],
    allow_credentials=True,
    allow_methods=['*'],
    allow_headers=['*'],
)


if MODE == "debug":
    logger.info("🔧 Starting in DEBUG mode - using MockBackendSender")
    backend = MockBackendSender()
else:
    logger.info("🚀 Starting in DEVELOP mode - connecting to backend at %s", BACKEND_WS_URL)
    backend = BackendSender(BACKEND_WS_URL)

stt_service = STTService(backend=backend)

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