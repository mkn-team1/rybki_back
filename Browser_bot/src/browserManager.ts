import { chromium, Browser, BrowserContext, Page } from "playwright";
import { logger } from "./logger";

let browserPromise: Promise<Browser> | null = null;

export async function getBrowser(): Promise<Browser> {
  if (!browserPromise) {
    logger.info("Launching Chromium browser");
    browserPromise = chromium.launch({
      headless: true,
      handleSIGINT: false,
      handleSIGTERM: false,
      handleSIGHUP: false,
      args: [
        "--headless=new",
        "--disable-gpu",
        "--no-first-run",
        "--no-default-browser-check",
        "--no-service-autorun",
        "--disable-background-networking",
        "--disable-background-timer-throttling",
        "--disable-backgrounding-occluded-windows",
        "--disable-renderer-backgrounding",
        "--disable-breakpad",
        "--disable-component-update",
        "--disable-domain-reliability",
        "--disable-client-side-phishing-detection",
        "--disable-sync",
        "--metrics-recording-only",
        "--safebrowsing-disable-auto-update",
        "--no-pings",
        "--disable-features=Translate,BackForwardCache,AcceptCHFrame,MediaRouter",
        "--hide-scrollbars",
        "--disable-notifications",
        "--disable-default-apps",
        "--disable-popup-blocking",
        "--remote-debugging-port=0",
        "--disk-cache-dir=/tmp/chrome-cache",
        "--disk-cache-size=10000000",
        "--media-cache-size=10000000",
        "--disable-dev-shm-usage",
        "--disable-extensions",
        "--disable-plugins",
        "--mute-audio",
        "--no-zygote",
        "--disable-speech-api",
        "--disable-images",
        "--aggressive-cache-discard",
        "--disable-hang-monitor",
        "--disable-ipc-flooding-protection",
        "--disable-gpu-rasterization",

        '--ignore-certificate-errors',
      ]
    });
  }
  return browserPromise;
}

export async function closeBrowser(): Promise<void> {
  if (!browserPromise) {
    return;
  }
  const browser = await browserPromise;
  await browser.close();
  browserPromise = null;
  logger.info("Chromium browser closed");
}

export async function newContextAndPage(): Promise<{ context: BrowserContext; page: Page }> {
  const browser = await getBrowser();
  const context = await browser.newContext({
    viewport: { width: 320, height: 480 },
    ignoreHTTPSErrors: true // TODO: в продакшене лучше убрать и нормально настроить сертификаты
  });
  const page = await context.newPage();
  return { context, page };
}
