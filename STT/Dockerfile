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

# Установка torch CPU-only ПЕРЕД speechbrain
RUN --mount=type=cache,target=/root/.cache/uv \
    if [ "$MODEL" = "Vosk" ]; then \
        uv pip install --system torch==2.8.0 torchaudio==2.8.0 --index-url https://download.pytorch.org/whl/cpu; \
    else \
        uv pip install --system torch==2.8.0 torchaudio==2.8.0; \
    fi

# Установка speechbrain БЕЗ зависимостей (torch уже установлен)
RUN --mount=type=cache,target=/root/.cache/uv \
    uv pip install --system --no-deps speechbrain==1.0.3

COPY requirements-speechbrain.txt .
# Установка зависимостей speechbrain
RUN --mount=type=cache,target=/root/.cache/uv \
    uv pip install --system -r requirements-speechbrain.txt

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

# Заменяем whisper_model.py на stub если MODEL=Vosk
RUN if [ "$MODEL" = "Vosk" ]; then \
        echo "Using stub WhisperManager..." && \
        cp app/whisper_model_stub.py app/whisper_model.py; \
    fi

EXPOSE 8081

CMD ["python", "main.py"]
