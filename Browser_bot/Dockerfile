FROM node:20-slim

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates wget dumb-init \
    libnss3 libasound2 libx11-6 libx11-xcb1 libxcb1 libxcomposite1 \
    libxcursor1 libxdamage1 libxext6 libxfixes3 libxi6 libxrandr2 \
    libgbm1 libgtk-3-0 libxshmfence1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY package.json tsconfig.json ./
RUN npm install

# Устанавливаем браузер и системные зависимости как root
RUN npx playwright install --with-deps chromium

# Создаём пользователя и отдаём права
RUN useradd -m -u 1001 botuser && chown -R botuser:botuser /app

# Копируем кеш браузеров из /root в домашний каталог botuser, чтобы рантайм нашёл исполняемые файлы
RUN mkdir -p /home/botuser/.cache && cp -r /root/.cache/ms-playwright /home/botuser/.cache/ && chown -R botuser:botuser /home/botuser/.cache/ms-playwright

USER botuser

COPY src ./src
RUN npm run build

ENV NODE_ENV=development
ENV MAX_CONCURRENT_BOTS=4 \
    TASK_POLL_INTERVAL_MS=1000

CMD ["dumb-init", "node", "dist/index.js"]
