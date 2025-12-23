import { Page } from "playwright";
import { JoinConferenceTask } from "../taskTypes";
import { BasePlatformConnector } from "./basePlatform";

export class KonturTalkConnector extends BasePlatformConnector {
  platformName = "kontur_talk";

  protected getInMeetingSelector(): string {
      return ".toolbar-buttons._center"; // Селектор тулбара в комнате
  }

  protected async joinRoom(page: Page, task: JoinConferenceTask): Promise<void> {
    // Ввод имени
    const form = page.locator("form").first();
    await form.waitFor({ timeout: 20000 });
    await form.locator("input").nth(0).fill("AI Assistant");
    await page.locator("div.action.submit-button button").first().click();

    // Экран проверки устройств -> Кнопка "Присоединиться"
    const toolbar = page.locator("div.toolbar").first();
    await toolbar.waitFor({ timeout: 20000 });
    await toolbar.locator("tl-button.submit button").first().click();
  }

  protected async performHangup(page: Page): Promise<void> {
      // Ищем кнопку выхода
      const hangupBtn = page.locator("#hangup-btn button").first();
      if (await hangupBtn.isVisible()) {
          await hangupBtn.click();
      }
  }
}
