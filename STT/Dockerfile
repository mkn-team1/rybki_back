FROM python:3.12-slim

WORKDIR /app

# Устанавливаем зависимости
COPY requirements.txt .
RUN pip install --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt

# Копируем код
COPY . .

# ENV переменные
ENV MODEL=${MODEL}
ENV MIN_SEND_S=${MIN_SEND_S}
ENV PREFERRED_SEND_S=${PREFERRED_SEND_S}
ENV MAX_SEND_S=${MAX_SEND_S}
ENV SILENCE_AFTER_S=${SILENCE_AFTER_S}
ENV CHAR_THRESHOLD=${CHAR_THRESHOLD}
ENV TIME_THRESHOLD=${TIME_THRESHOLD}

EXPOSE 8081

CMD ["python", "main.py"]
