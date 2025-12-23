# syntax=docker/dockerfile:1
FROM python:3.12-slim

WORKDIR /app

# Системные зависимости
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    wget \
    unzip \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Обновление pip
RUN pip install --upgrade pip
    
# Установка uv для ускорения
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-cache-dir uv

# Установка зависимостей
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/uv \
    uv pip install --system -r requirements.txt

# Копируем код
COPY . .


EXPOSE 8081

CMD ["python", "main.py"]
