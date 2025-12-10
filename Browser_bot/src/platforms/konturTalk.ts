import { Page } from "playwright";
import { JoinConferenceTask } from "../taskTypes";
import { PlatformConnector } from "./basePlatform";
import { installWebRtcAudioHook } from "../audioStreamer";
import { logger } from "../logger";

export class KonturTalkConnector implements PlatformConnector {
  async joinAndStream(
    task: JoinConferenceTask,
    page: Page,
    audioWsUrl: string,
    signal: AbortSignal
  ): Promise<void> {
    logger.info(
      { botId: task.botId, url: task.meetingUrl },
      "KonturTalk joinAndStream start"
    );

    await installWebRtcAudioHook(page, audioWsUrl, task.botId);

    await page.goto(task.meetingUrl, { waitUntil: "networkidle" });

    const form = page.locator("form").first();
    await form.waitFor({ timeout: 15000 });

    const inputs = form.locator("input");
    const nameInput = inputs.nth(0);
    await nameInput.fill("AI Assistant");

    const continueBtn = page
      .locator("div.action.submit-button")
      .locator("button")
      .first();
    await continueBtn.click();

    const toolbar = page.locator("div.toolbar").first();
    await toolbar.waitFor({ timeout: 20000 });

    const joinBtn = toolbar.locator("tl-button.submit").locator("button").first();
    await joinBtn.click();

    await page.locator(".grid-participant").first().waitFor({ timeout: 30000 });

    logger.info({ botId: task.botId }, "Joined conference successfully");


    const abortPromise = new Promise<void>((resolve) => {
        if (signal.aborted) {
            resolve();
        } else {
            signal.addEventListener('abort', () => resolve(), { once: true });
        }
    });

    const pageClosePromise = new Promise<void>((_, reject) => {
        if (page.isClosed()) {
             reject(new Error("Page closed unexpectedly"));
        } else {
             page.on('close', () => reject(new Error("Page closed unexpectedly")));
        }
    });

    try {
        await Promise.race([abortPromise, pageClosePromise]);
        
        logger.info({ botId: task.botId }, "Received shutdown signal, leaving conference...");

        if (!page.isClosed()) {
            const hangupBtn = page.locator("#hangup-btn button").first();
            
            try {
                if (await hangupBtn.isVisible({ timeout: 2000 })) {
                    await hangupBtn.click();
                    logger.info({ botId: task.botId }, "Clicked hangup button");
                    await page.waitForTimeout(1000); 
                } else {
                     logger.warn({ botId: task.botId }, "Hangup button not visible");
                }
            } catch (clickErr) {
                 logger.warn({ botId: task.botId, err: clickErr }, "Failed to click hangup button");
            }
        }
    } catch (err) {
        logger.warn({ botId: task.botId, err }, "Conference session ended abruptly");
    }

    logger.info({ botId: task.botId }, "KonturTalk session finished");
  
  }
}
