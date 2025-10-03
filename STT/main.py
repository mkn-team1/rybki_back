import logging
from fastapi import FastAPI, WebSocket

logging.basicConfig(level=logging.INFO)

app = FastAPI()

@app.websocket("/ws/stt")
async def websocket_endpoint(websocket: WebSocket):
    pass
