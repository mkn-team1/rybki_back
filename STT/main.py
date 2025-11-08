import uvicorn
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI, WebSocket
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from app.stt_service import STTService
from config import MODE, STT_PORT

logging.basicConfig(
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
    level=(logging.INFO if MODE == "develop" else logging.DEBUG)
)

logger = logging.getLogger(__name__)

logger.info("🚀 Starting STT service - waiting for backend connection")

READY = False

try:
    stt_service = STTService()
    READY = True
except Exception as e:
    logger.error(f"❌ Failed to initialize STT service: {e}")
    raise


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

@app.get("/health")
async def health():
    if not READY:
        logger.warning("Health check failed - service not ready")
        return JSONResponse(
            status_code=503,
            content={"status": "loading", "message": "Models are being loaded"}
        )
    return {"status": "ok", "message": "STT service is ready"}


@app.websocket("/ws/stt")
async def websocket_endpoint(websocket: WebSocket):
    await stt_service.handle_backend_connection(websocket)

if __name__ == "__main__":
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=STT_PORT,
        ws_ping_interval=20,
        ws_ping_timeout=20,
        timeout_keep_alive=5
    )