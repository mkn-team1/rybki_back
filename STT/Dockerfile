# syntax=docker/dockerfile:1
FROM python:3.12-slim

WORKDIR /app

ARG MODEL

# Системные зависимости
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Обновление pip
RUN pip install --upgrade pip
    
# Установка uv для ускорения
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-cache-dir uv

# Установка базовых Python зависимостей
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/uv \
    uv pip install --system -r requirements.txt

# Условная установка Whisper зависимостей
COPY requirements-whisper.txt .
RUN --mount=type=cache,target=/root/.cache/uv \
    if [ "$MODEL" = "Whisper" ]; then \
        uv pip install --system -r requirements-whisper.txt; \
    fi

# Копирование скрипта загрузки моделей
COPY download_models.py /tmp/

# Загрузка моделей в зависимости от ARG MODEL
RUN python /tmp/download_models.py --model ${MODEL} && \
    rm /tmp/download_models.py

# Копируем код
COPY . .

#Создание stub для WhisperManager если MODEL=Vosk
RUN if [ "$MODEL" = "Vosk" ]; then \
        echo "Creating stub WhisperManager..." && \
        rm -f /app/whisper_model.py && \
        echo 'class WhisperManager:\n    def __init__(self):\n        raise NotImplementedError("Whisper not available in this build")\n    def transcribe(self, *args, **kwargs):\n        raise NotImplementedError("Whisper not available in this build")' > /app/whisper_model.py; \
    fi


EXPOSE 8081

CMD ["python", "main.py"]
