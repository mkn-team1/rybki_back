import os

STT_PORT = int(os.getenv("STT_PORT", "8081"))

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

MODE = os.getenv("MODE", "develop") # "develop" / "debug"

WHISPER_MODEL = os.path.join(BASE_DIR, "models", "whisper", "small") 

SAMPLE_RATE = int(os.getenv("SAMPLE_RATE", "16000"))

# Params for Whisper
THREADS = int(os.getenv("THREADS", "12")) # Based on CPU
BEAM_SIZE = int(os.getenv("BEAM_SIZE", "1"))
COMPUTE_TYPE = os.getenv("COMPUTE_TYPE", None) # If not set, defaults to "float16" (GPU) or  "int8" (CPU)
DEVICE = os.getenv("DEVICE", None) # If not set, defaults to "cuda" (GPU) if available, else "cpu" (CPU)


# Adaptive thresholds
MIN_SEND_S = float(os.getenv("MIN_SEND_S", "5"))
PREFERRED_SEND_S = float(os.getenv("PREFERRED_SEND_S", "10.0"))
CHAR_THRESHOLD = int(os.getenv("CHAR_THRESHOLD", "1200"))
TIME_THRESHOLD = float(os.getenv("TIME_THRESHOLD", "20.0"))

