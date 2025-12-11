import { Kafka, Consumer } from "kafkajs";
import Redis from "ioredis";
import { Config, TaskSourceType } from "./config";
import { JoinConferenceTask } from "./taskTypes";
import { logger } from "./logger";

export interface ITaskSource {
  init(): Promise<void>;
  fetchNextTask(): Promise<JoinConferenceTask | null>;
  close(): Promise<void>;
}

// ==========================================
// 1. KAFKA IMPLEMENTATION
// ==========================================
class KafkaTaskSource implements ITaskSource {
  private kafka: Kafka;
  private consumer: Consumer;
  private messageBuffer: JoinConferenceTask[] = [];

  constructor(private cfg: Config) {
    this.kafka = new Kafka({
      clientId: cfg.workerId,
      brokers: cfg.kafkaBrokers,
    });
    this.consumer = this.kafka.consumer({ 
        groupId: cfg.kafkaGroupId,
        sessionTimeout: 30000 
    });
  }

  async init(): Promise<void> {
    await this.consumer.connect();
    await this.consumer.subscribe({ topic: this.cfg.kafkaTopic, fromBeginning: false });

    // Запускаем фоновый процесс вычитывания
    this.consumer.run({
      autoCommit: false,
      eachBatch: async ({ batch, resolveOffset, commitOffsetsIfNecessary, heartbeat, isRunning }) => {
        for (const message of batch.messages) {
          if (!isRunning()) break;
          
          // BACKPRESSURE: Если буфер переполнен (например > 5), ждем.
          // Это заставляет KafkaJS перестать запрашивать новые батчи у брокера.
          while (this.messageBuffer.length >= 5 && isRunning()) {
            await new Promise(r => setTimeout(r, 500));
            await heartbeat(); // Важно: шлем хартбиты, чтобы брокер не выкинул нас из группы
          }

          if (message.value) {
            try {
              const taskPayload = JSON.parse(message.value.toString());
              if (taskPayload.botId && taskPayload.meetingUrl) {
                this.messageBuffer.push(taskPayload);
                logger.info({ botId: taskPayload.botId }, "Buffered task from Kafka");
              }
            } catch (e) {
              logger.error(`Error parsing Kafka msg: ${e}`);
            }
            
            // Сразу помечаем сообщение как "полученное" (resolved).
            resolveOffset(message.offset);
          }
        }
        
        // Фиксируем оффсеты в брокере после обработки (или буферизации) батча
        await commitOffsetsIfNecessary();
      }
    });
    
    logger.info("KafkaTaskSource initialized");
  }

  async fetchNextTask(): Promise<JoinConferenceTask | null> {
    return this.messageBuffer.shift() || null;
  }

  async close(): Promise<void> {
    await this.consumer.disconnect();
  }
}

// ==========================================
// 2. REDIS IMPLEMENTATION
// ==========================================
class RedisTaskSource implements ITaskSource {
  private redis: Redis;

  constructor(private cfg: Config) {
    this.redis = new Redis(cfg.redisUrl);
  }

  async init(): Promise<void> {
    await this.redis.ping();
    logger.info("RedisTaskSource initialized");
  }

  async fetchNextTask(): Promise<JoinConferenceTask | null> {
    const result = await this.redis.lpop(this.cfg.redisQueueKey);
    
    if (result) {
      try {
        const task = JSON.parse(result);
        logger.info({ botId: task.botId }, "Fetched task from Redis");
        return task;
      } catch (e) {
        logger.error(`Error parsing Redis task: ${e}`);
      }
    }
    return null;
  }

  async close(): Promise<void> {
    await this.redis.quit();
  }
}

export function createTaskSource(cfg: Config): ITaskSource {
  switch (cfg.taskSourceType) {
    case "kafka":
      return new KafkaTaskSource(cfg);
    case "redis":
      return new RedisTaskSource(cfg);
    default:
      throw new Error(`Unknown task source type: ${cfg.taskSourceType}`);
  }
}
