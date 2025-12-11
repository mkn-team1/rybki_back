export type TaskSourceType = "kafka" | "redis";

export interface Config {
  workerId: string;
  maxConcurrentBots: number;
  taskPollIntervalMs: number;
  logLevel: string;

  // Выбор источника задач
  taskSourceType: TaskSourceType;

  // Audio & Backend
  backendRestUrl: string;
  audioWsUrl: string;
  
  // Kafka
  kafkaBrokers: string[];
  kafkaGroupId: string;
  kafkaTopic: string;

  // Redis
  redisUrl: string;
  redisQueueKey: string;
}

export function loadConfig(): Config {
  const workerId = process.env.WORKER_ID || `worker-${Math.random().toString(36).slice(2, 8)}`;
  const maxConcurrentBots = parseInt(process.env.MAX_CONCURRENT_BOTS || "4", 10);
  const taskPollIntervalMs = parseInt(process.env.TASK_POLL_INTERVAL_MS || "1000", 10);
  const logLevel = process.env.LOG_LEVEL || "info";

  const taskSourceType = (process.env.TASK_SOURCE_TYPE as TaskSourceType) || "kafka"
  
  const backendRestUrl = process.env.BACKEND_REST_URL || "http://spring-backend:8080";
  const audioWsUrl = process.env.AUDIO_WS_URL || "ws://spring-backend:8080/ws/client";

  const kafkaBrokers = (process.env.KAFKA_BROKERS || "localhost:9092").split(",");
  const kafkaGroupId = process.env.KAFKA_GROUP_ID || "browser-bot-group";
  const kafkaTopic = process.env.KAFKA_TOPIC || "bot-tasks";

  const redisUrl = process.env.REDIS_URL || "redis://localhost:6379";
  const redisQueueKey = process.env.REDIS_QUEUE_KEY || "bot_tasks_queue";

  return {
    workerId,
    backendRestUrl,
    audioWsUrl,
    maxConcurrentBots,
    taskPollIntervalMs,
    logLevel,
    taskSourceType,
    kafkaBrokers,
    kafkaGroupId,
    kafkaTopic,
    redisUrl,
    redisQueueKey
  };
}
