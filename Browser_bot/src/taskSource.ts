import axios from "axios";
import { createClient, RedisClientType } from "redis";
import { JoinConferenceTask } from "./taskTypes";
import { Config } from "./config";
import { logger } from "./logger";

export interface TaskSource {
  init(): Promise<void>;
  fetchNextTask(): Promise<JoinConferenceTask | null>;
  reportTaskStarted(botId: string): Promise<void>;
  reportTaskFinished(
    botId: string,
    ok: boolean,
    errorMessage?: string
  ): Promise<void>;
}

/**
 * Источник для реальной очереди (Redis, позже можно добавить Kafka).
 * Сейчас пример с Redis — закомментирован fetchNextTask.
 */
class RedisTaskSource implements TaskSource {
  private redis: RedisClientType;

  constructor(private cfg: Config) {
    this.redis = createClient({ url: this.cfg.redisUrl });
    this.redis.on("error", (err) => {
      logger.error({ err }, "Redis client error");
    });
  }

  async init(): Promise<void> {
    await this.redis.connect();
    logger.info({ redisUrl: this.cfg.redisUrl }, "Connected to Redis");
  }

  async fetchNextTask(): Promise<JoinConferenceTask | null> {
    try {
      // Пример кода для продакшена (пока закомментирован):
      //
      // const timeoutSeconds = 5;
      // const res = await this.redis.brPop(this.cfg.redisQueueKey, timeoutSeconds);
      // if (!res) {
      //   return null;
      // }
      // const raw = res.element;
      // const task: JoinConferenceTask = JSON.parse(raw);
      // return task;
      //
      return null;
    } catch (err: any) {
      logger.error({ err }, "Failed to fetch task from Redis");
      return null;
    }
  }

  async reportTaskStarted(botId: string): Promise<void> {
    try {
      // await axios.post(
      //   `${this.cfg.backendBaseUrl}/api/worker/task-started`,
      //   { botId, workerId: this.cfg.workerId }
      // );
    } catch (err: any) {
      logger.error({ err, botId }, "Failed to report task started");
    }
  }

  async reportTaskFinished(
    botId: string,
    ok: boolean,
    errorMessage?: string
  ): Promise<void> {
    try {
      // await axios.post(
      //   `${this.cfg.backendBaseUrl}/api/worker/task-finished`,
      //   { botId, workerId: this.cfg.workerId, ok, errorMessage }
      // );
    } catch (err: any) {
      logger.error({ err, botId }, "Failed to report task finished");
    }
  }
}

/**
 * Тестовый источник: один раз возвращает захардкоженную задачу.
 * URL конференции подставишь в ENV TEST_MEETING_URL или прямо сюда.
 */
class SingleTaskSource implements TaskSource {
  private used = false;

  constructor(private cfg: Config) {}

  async init(): Promise<void> {}

  async fetchNextTask(): Promise<JoinConferenceTask | null> {
    if (this.used) {
      return null;
    }
    this.used = true;

    const task: JoinConferenceTask = {
      botId: "test-bot-1",
      meetingUrl: "https://yo3cll2q.ktalk.ru/w894l5ok5c8j",
      platform: "kontur_talk"
    };
    return task;
  }

  async reportTaskStarted(botId: string): Promise<void> {}

  async reportTaskFinished(
    botId: string,
    ok: boolean,
    errorMessage?: string
  ): Promise<void> {}
}

/**
 * Фабрика источников задач.
 */
export function createTaskSource(cfg: Config): TaskSource {
    return new SingleTaskSource(cfg);
}
