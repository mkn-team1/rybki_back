import { loadConfig } from "./config";
import { logger } from "./logger";
import { createTaskSource } from "./taskSource";
import { newContextAndPage, closeBrowser } from "./browserManager";
import { JoinConferenceTask } from "./taskTypes";
import { KonturTalkConnector } from "./platforms/konturTalk";
import { PlatformConnector } from "./platforms/basePlatform";

const cfg = loadConfig();
const taskSource = createTaskSource(cfg);

let activeBots = 0;
let isShuttingDown = false;
const runningBots = new Set<AbortController>();

function resolveConnector(platform: JoinConferenceTask["platform"]): PlatformConnector {
  switch (platform) {
    case "kontur_talk":
      return new KonturTalkConnector();
    default:
      throw new Error(`Unsupported platform: ${platform}`);
  }
}

async function runBot(task: JoinConferenceTask): Promise<void> {
  if (isShuttingDown) return;

  logger.info({ botId: task.botId }, "Bot starting");
  const { context, page } = await newContextAndPage();

  const abortController = new AbortController();
  runningBots.add(abortController);

  page.on("console", (msg) => {
    const text = msg.text();

    if (text.startsWith("[audio-bot]")) {
      logger.info(
        { botId: task.botId, type: msg.type() },
        text
      );
    } else if (msg.type() === "error") {
      logger.error(
        { botId: task.botId, type: msg.type() },
        text
      );
    }
  });


  page.on("pageerror", (err) => {
    logger.error({ botId: task.botId, err }, "Page error");
  });

  const connector = resolveConnector(task.platform);

  try {
    await taskSource.reportTaskStarted(task.botId);
    await connector.joinAndStream(task, page, cfg.audioWsUrl, abortController.signal);
    await taskSource.reportTaskFinished(task.botId, true);
  } catch (err: any) {
    if (!isShuttingDown) {
      logger.error({ botId: task.botId, err }, "Bot failed");
      await taskSource.reportTaskFinished(
        task.botId,
        false,
        err?.message || String(err)
      );
    }
  } finally {
    runningBots.delete(abortController);
    await context.close().catch(() => {});
    activeBots -= 1;
    logger.info({ activeBots }, "Bot finished");
  }
}

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms));
}

async function mainLoop() {
  logger.info(
    { workerId: cfg.workerId, maxConcurrentBots: cfg.maxConcurrentBots },
    "Browser-bot worker starting"
  );

  await taskSource.init();

  let singleRun = true;

  // eslint-disable-next-line no-constant-condition
  while (!isShuttingDown) {
    try {
      if (activeBots >= cfg.maxConcurrentBots) {
        await sleep(cfg.taskPollIntervalMs);
        continue;
      }

      if (singleRun) {
        const task = await taskSource.fetchNextTask();
        if (!task) {
          await sleep(cfg.taskPollIntervalMs);
          continue;
        }
        activeBots += 1;
        void runBot(task);
        singleRun = false;
      } else {
        await sleep(cfg.taskPollIntervalMs);
      }
    } catch (err: any) {
      if (!isShuttingDown) {
        logger.error({ err }, "Error in main loop");
        await sleep(cfg.taskPollIntervalMs);
      }
    }
  }
}

async function shutdown(signal: string) {
  if (isShuttingDown) return;
  isShuttingDown = true;

  logger.info({ signal }, "Shutting down worker");

  
  logger.info(`Signaling ${runningBots.size} bots to leave conference...`);
  for (const controller of runningBots) {
    controller.abort();
  }

  const shutdownStart = Date.now();
  while (activeBots > 0 && Date.now() - shutdownStart < 10000) {
    await sleep(500);
  }
  
  if (activeBots > 0) {
    logger.warn("Some bots did not finish in time, force closing browser.");
  } else {
    logger.info("All bots finished gracefully.");
  }

  setTimeout(() => {
    logger.error("Force exit after shutdown timeout");
    process.exit(1);
  }, 10000);
  
  try {
    await closeBrowser();
    process.exit(0);
  } catch (err) {
    logger.error({ err }, "Error during shutdown");
    process.exit(1);
  }
}

process.on("SIGTERM", () => void shutdown("SIGTERM"));
process.on("SIGINT", () => void shutdown("SIGINT"));

mainLoop().catch((err) => {
  logger.fatal({ err }, "Fatal error in worker");
  process.exit(1);
});
