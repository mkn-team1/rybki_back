import { loadConfig } from "./config";
import { logger } from "./logger";
import { createTaskSource, ITaskSource } from "./taskSource";
import { JoinConferenceTask } from "./taskTypes";
import { newContextAndPage, closeBrowser } from "./browserManager";
import { BasePlatformConnector } from "./platforms/basePlatform";
import { KonturTalkConnector } from "./platforms/konturTalk";

const cfg = loadConfig();

const connectors: Record<string, BasePlatformConnector> = {
  "kontur_talk": new KonturTalkConnector(),
};

let isShuttingDown = false;
const runningBots = new Set<AbortController>();
let taskSource: ITaskSource;

/**
 * Основная функция запуска одного бота
 */
async function runBot(task: JoinConferenceTask): Promise<void> {
  if (isShuttingDown) return;

  logger.info({ botId: task.botId, platform: task.platform }, "Starting bot session");

  // Контроллер для прерывания работы конкретного бота
  const abortController = new AbortController();
  runningBots.add(abortController);

  const { context, page } = await newContextAndPage();

  try {
    const connector = connectors[task.platform];
    if (!connector) {
      throw new Error(`Platform '${task.platform}' is not supported. Available: ${Object.keys(connectors).join(", ")}`);
    }

    page.on("console", (msg) => {
        const text = msg.text();
        if (text.includes("[BOT_COMMAND:LEAVE]")) {
            logger.info({ botId: task.botId }, "Received LEAVE command via console, aborting...");
            abortController.abort();
        }

        if (text.startsWith("[audio-bot]")) {
            logger.info({ botId: task.botId, type: msg.type() }, text);
        } else if (msg.type() === "error") {
            logger.error({ botId: task.botId, type: msg.type() }, text);
        }
    });

    await connector.execute(task, page, abortController.signal);

  } catch (error: any) {
    logger.error({ botId: task.botId, error: error.message }, "Bot session failed with error");
  } finally {

    runningBots.delete(abortController);
    
    try {
      await context.close();
    } catch (e) {
      logger.warn({ botId: task.botId, err: e }, "Error closing context");
    }
  }
}

/**
 * Главный цикл поллинга задач
 */
async function mainLoop() {
  logger.info(`Worker ${cfg.workerId} started. Max bots: ${cfg.maxConcurrentBots}. Source: ${cfg.taskSourceType}`);

  taskSource = createTaskSource(cfg);
  try {
    await taskSource.init();
  } catch (e) {
    logger.fatal(`Failed to init task source: ${e}`);
    process.exit(1);
  }

  while (!isShuttingDown) {
    if (runningBots.size < cfg.maxConcurrentBots) {
      try {
        const task = await taskSource.fetchNextTask();
        
        if (task) {
          runBot(task).catch((err) => {
             logger.error({ err }, "Unexpected error in runBot promise");
          });
        } else {
          await new Promise((r) => setTimeout(r, cfg.taskPollIntervalMs));
        }
      } catch (err) {
        logger.error({ err }, "Error fetching next task");
        await new Promise((r) => setTimeout(r, 5000));
      }
    } else {
      await new Promise((r) => setTimeout(r, 1000));
    }
  }
}

/**
 * Graceful Shutdown (SIGINT / SIGTERM)
 */
async function shutdown() {
  if (isShuttingDown) return;
  isShuttingDown = true;
  logger.info("Shutting down worker...");

  if (taskSource) {
    await taskSource.close();
  }

  logger.info(`Stopping ${runningBots.size} active bots...`);
  for (const controller of runningBots) {
    controller.abort();
  }

  await new Promise(r => setTimeout(r, 2000));

  await closeBrowser();
  logger.info("Browser closed. Bye.");
  process.exit(0);
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);

mainLoop().catch((err) => {
  logger.fatal({ err }, "Main loop crashed");
  process.exit(1);
});
