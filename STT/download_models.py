"""
Скрипт загрузки моделей для STT сервиса.
Поддерживает Vosk, Whisper и Silero VAD.
"""
import os
import sys
import logging
import urllib.request
import zipfile
import subprocess
import argparse
from pathlib import Path

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Директория для моделей
MODELS_DIR = Path("/app/models")
MODELS_DIR.mkdir(parents=True, exist_ok=True)

# URL моделей Vosk
VOSK_MODELS = {
    "ru": "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
    "en": "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
}


def download_file(url: str, dest_path: Path):
    """Загрузка файла с прогресс-баром"""
    logger.info(f"Downloading {url}...")
    
    def progress_hook(count, block_size, total_size):
        if total_size > 0:
            percent = int(count * block_size * 100 / total_size)
            sys.stdout.write(f"\rProgress: {percent}%")
            sys.stdout.flush()
    
    try:
        urllib.request.urlretrieve(url, dest_path, reporthook=progress_hook)
        sys.stdout.write("\n")
        logger.info(f"Downloaded to {dest_path}")
    except Exception as e:
        logger.error(f"Failed to download {url}: {e}")
        raise


def download_vosk_models():
    """Загрузка моделей Vosk"""
    logger.info("=== Downloading Vosk models ===")
    
    for lang, url in VOSK_MODELS.items():
        zip_path = MODELS_DIR / f"vosk-{lang}.zip"
        
        # Загрузка
        download_file(url, zip_path)
        
        # Распаковка
        logger.info(f"Extracting {lang} model...")
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(MODELS_DIR)
        
        # Удаление архива
        zip_path.unlink()
        logger.info(f"Vosk {lang} model installed")
    
    # Проверка
    extracted_dirs = list(MODELS_DIR.glob("vosk-model-*"))
    logger.info(f"Vosk models extracted: {[d.name for d in extracted_dirs]}")


def download_silero_vad():
    """Загрузка Silero VAD через torch.hub"""
    logger.info("=== Downloading Silero VAD ===")
    
    try:
        import torch
        
        # Загрузка модели в кэш torch.hub
        model, utils = torch.hub.load(
            repo_or_dir="snakers4/silero-vad",
            model="silero_vad",
            force_reload=False,
            trust_repo=True
        )
        logger.info("Silero VAD downloaded successfully")
    except Exception as e:
        logger.error(f"Failed to download Silero VAD: {e}")
        raise


def download_whisper_model():
    """Загрузка и конвертация Whisper Turbo модели"""
    logger.info("=== Downloading Whisper Turbo model ===")
    
    # Установка зависимостей для конвертации
    logger.info("Installing transformers and ctranslate2...")
    try:
        subprocess.run([
            sys.executable, "-m", "pip", "install", "--no-cache-dir",
            "transformers[torch]>=4.23",
            "ctranslate2>=3.0"
        ], check=True)
    except subprocess.CalledProcessError as e:
        logger.error(f"Failed to install dependencies: {e}")
        raise
    
    # Директория для конвертированной модели
    output_dir = MODELS_DIR / "large-v3-turbo-ct2"
    
    # Конвертация модели с Hugging Face в CTranslate2 формат
    logger.info("Converting Whisper model to CTranslate2 format (this may take 5-10 minutes)...")
    try:
        subprocess.run([
            "ct2-transformers-converter",
            "--model", "openai/whisper-large-v3-turbo",
            "--output_dir", str(output_dir),
            "--copy_files", "tokenizer.json", "preprocessor_config.json", "generation_config.json",
            "--quantization", "int8"
        ], check=True)
    except subprocess.CalledProcessError as e:
        logger.error(f"Model conversion failed: {e}")
        raise
    
    logger.info(f"Whisper Turbo model converted and saved to {output_dir}")
    
    # Проверка
    if output_dir.exists():
        files = list(output_dir.glob("*"))
        logger.info(f"Model files ({len(files)}): {[f.name for f in files[:5]]}...")
    else:
        raise RuntimeError("Model conversion failed - output directory not found")


def main():
    parser = argparse.ArgumentParser(description="Download STT models")
    parser.add_argument(
        "--model",
        type=str,
        default="Vosk",
        choices=["Vosk", "Whisper"],
        help="Model type to download (Vosk or Whisper)"
    )
    args = parser.parse_args()
    
    logger.info(f"Starting model download for: {args.model}")
    logger.info(f"Models directory: {MODELS_DIR}")
    
    try:
        # Всегда загружаем Silero VAD (используется в обоих режимах)
        download_silero_vad()
        
        # Загружаем модели в зависимости от типа
        if args.model == "Vosk":
            download_vosk_models()
        elif args.model == "Whisper":
            download_whisper_model()
        else:
            raise ValueError(f"Unknown model type: {args.model}")
        
        logger.info("=== All models downloaded successfully ===")
        
        total_size = sum(f.stat().st_size for f in MODELS_DIR.rglob('*') if f.is_file())
        logger.info(f"Total models size: {total_size / (1024**2):.2f} MB")
        
    except Exception as e:
        logger.error(f"Model download failed: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
