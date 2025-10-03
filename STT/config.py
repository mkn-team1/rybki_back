import os

VOSK_MODEL_RU = os.getenv("VOSK_MODEL_RU", "./models/vosk-model-small-ru-0.22")
VOSK_MODEL_EN = os.getenv("VOSK_MODEL_EN", "./models/vosk-model-small-en-us-0.15")
BACKEND_WS_URL = os.getenv("BACKEND_WS_URL", "ws://backend:8080/stt-ingest")

SAMPLE_RATE = int(os.getenv("SAMPLE_RATE", "16000"))
CHAR_THRESHOLD = int(os.getenv("CHAR_THRESHOLD", "1200"))
TIME_THRESHOLD = float(os.getenv("TIME_THRESHOLD", "20.0"))
CHUNK_SIZE = int(os.getenv("CHUNK_SIZE", "4000"))