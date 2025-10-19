import os

STT_HOST = os.getenv("STT_HOST", "localhost")
STT_PORT = int(os.getenv("STT_PORT", "8081"))

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

MODE = os.getenv("MODE", "develop") # "develop" / "debug"
MODEL = os.getenv("MODEL", "Vosk") # "Vosk" / "Whisper"

VOSK_MODEL_RU = os.path.join(BASE_DIR, "models", "vosk-model-small-ru-0.22")
VOSK_MODEL_EN = os.path.join(BASE_DIR, "models", "vosk-model-small-en-us-0.15")
WHISPER_MODEL = os.path.join(BASE_DIR, "models", "large-v3-turbo-ct2") 

SAMPLE_RATE = int(os.getenv("SAMPLE_RATE", "16000"))

# Params for Whisper
THREADS = int(os.getenv("THREADS", "12")) # Based on CPU
CHUNK_SIZE_S = int(os.getenv("CHUNK_SIZE_S", "10"))
BEAM_SIZE = int(os.getenv("BEAM_SIZE", "1"))
COMPUTE_TYPE = os.getenv("COMPUTE_TYPE", None) # If not set, defaults to "float16" (GPU) or  "int8" (CPU)
DEVICE = os.getenv("DEVICE", None) # If not set, defaults to "cuda" (GPU) if available, else "cpu" (CPU)


# Adaptive thresholds
MIN_SEND_S = float(os.getenv("MIN_SEND_S", "5"))
PREFERRED_SEND_S = float(os.getenv("PREFERRED_SEND_S", "8.0"))
MAX_SEND_S = float(os.getenv("MAX_SEND_S", "10.0"))
SILENCE_AFTER_S = float(os.getenv("SILENCE_AFTER_S", "0.5"))
CHAR_THRESHOLD = int(os.getenv("CHAR_THRESHOLD", "1200"))
TIME_THRESHOLD = float(os.getenv("TIME_THRESHOLD", "20.0"))

