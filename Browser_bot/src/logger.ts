import pino from "pino";
import { loadConfig } from "./config";

const cfg = loadConfig();

export const logger = pino({
  level: cfg.logLevel,
  transport: process.env.NODE_ENV === "production" ? undefined : {
    target: "pino-pretty",
    options: { colorize: true }
  }
});
