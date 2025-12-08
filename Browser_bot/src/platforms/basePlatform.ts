import { Page } from "playwright";
import { JoinConferenceTask } from "../taskTypes";
import axios from "axios";
import { logger } from "../logger";
import { installWebRtcAudioHook } from "../audioStreamer";
import { loadConfig } from "../config";

const cfg = loadConfig();

type notifyBackendStatus = "started" | "removed"

export abstract class BasePlatformConnector {
  abstract platformName: string;

  protected abstract joinRoom(page: Page, task: JoinConferenceTask): Promise<void>;
  protected abstract performHangup(page: Page): Promise<void>;
  protected abstract getInMeetingSelector(): string;

  async execute(task: JoinConferenceTask, page: Page, signal: AbortSignal): Promise<void> {
    const { botId } = task;

    try {
      const wsUrlWithParams = `${cfg.audioWsUrl}/${botId}`;
      
      await installWebRtcAudioHook(page, wsUrlWithParams, botId);

      await page.goto(task.meetingUrl, { waitUntil: "networkidle" });

      await this.joinRoom(page, task);

      const selector = this.getInMeetingSelector();
      await page.locator(selector).first().waitFor({ timeout: 60000 });
      logger.info({ botId }, "Joined conference successfully");

      // Уведомляем бэк: Успех
      await this.notifyBackend(botId, "started");

      // Ждем конца
      await this.waitForShutdown(page, signal, botId);

    } catch (error: any) {
      logger.error({ botId, error }, "Error in bot session");
      await this.notifyBackend(botId, "removed");
      throw error;
    } finally {
      await this.notifyBackend(botId, "removed");
    }
  }

  private async waitForShutdown(page: Page, signal: AbortSignal, botId: string): Promise<void> {
    return new Promise<void>((resolve) => {
        const checkInterval = setInterval(async () => {
             if (page.isClosed()) {
                 clearInterval(checkInterval);
                 resolve();
                 return;
             }
             try {
                const inMeeting = await page.locator(this.getInMeetingSelector()).count() > 0;
                if (!inMeeting) {
                    logger.info({ botId }, "Meeting ended (selector lost)");
                    clearInterval(checkInterval);
                    resolve();
                }
             } catch {}
        }, 2000);

        signal.addEventListener('abort', async () => {
            clearInterval(checkInterval);
            logger.info({ botId }, "Abort signal received, leaving...");
            if (!page.isClosed()) {
                try { await this.performHangup(page); } catch (e) { logger.error(`Hangup failed: ${e}`); }
                await page.waitForTimeout(2000);
            }
            resolve();
        });
    });
  }

  private async notifyBackend(botId: string, status: notifyBackendStatus) {
      try {
          
          await axios.post(`${cfg.backendRestUrl}/bot/${botId}/${status}`);
      } catch (e) {
          logger.warn({ botId, status, err: e }, "Failed to send status update");
      }
  }
}
