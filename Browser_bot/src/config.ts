export interface Config {
  workerId: string;
  backendBaseUrl: string;
  audioWsUrl: string;
  maxConcurrentBots: number;
  taskPollIntervalMs: number;
  logLevel: string;
  redisUrl: string;
  redisQueueKey: string;
}

export function loadConfig(): Config {
  const workerId = process.env.WORKER_ID || `worker-${Math.random().toString(36).slice(2, 8)}`;
  const backendBaseUrl = process.env.BACKEND_BASE_URL || "http://spring-backend:8080";
  const audioWsUrl = process.env.AUDIO_WS_URL || "ws://spring-backend:8080/ws/client";
  const maxConcurrentBots = Number(process.env.MAX_CONCURRENT_BOTS || "4");
  const taskPollIntervalMs = Number(process.env.TASK_POLL_INTERVAL_MS || "1000");
  const logLevel = process.env.LOG_LEVEL || "info";

  const redisHost = process.env.REDIS_HOST || "redis";
  const redisPort = process.env.REDIS_PORT || "6379";

  const redisUrl = `redis://${redisHost}:${redisPort}/0`;
  const redisQueueKey = process.env.REDIS_QUEUE_KEY || "meeting_tasks";

  return {
    workerId,
    backendBaseUrl,
    audioWsUrl,
    maxConcurrentBots,
    taskPollIntervalMs,
    logLevel,
    redisUrl,
    redisQueueKey
  };
}
