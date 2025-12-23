# bash
#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}"

echo "Waiting for Kafka at ${BOOTSTRAP_SERVER}..."

# Ждём, пока брокер начнёт отвечать
for i in {1..30}; do
  if kafka-topics --bootstrap-server "${BOOTSTRAP_SERVER}" --list >/dev/null 2>&1; then
    echo "Kafka is up!"
    break
  fi
  echo "Kafka is not ready yet, retry ${i}/30..."
  sleep 2
done

echo "Creating topics if not exist..."

# Создаём топик для задач ботов
kafka-topics \
  --bootstrap-server "${BOOTSTRAP_SERVER}" \
  --create \
  --if-not-exists \
  --topic ${BOT_TASKS_TOPIC:-meeting-bot-tasks} \
  --replication-factor 1 \
  --partitions ${NUMBER_OF_PARTITIONS:-1}

echo "Existing topics:"
kafka-topics --bootstrap-server "${BOOTSTRAP_SERVER}" --list

echo "Kafka init script completed successfully."
