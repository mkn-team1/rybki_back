import { Page } from "playwright";
import { JoinConferenceTask } from "../taskTypes";

export interface PlatformConnector {
  joinAndStream(task: JoinConferenceTask, page: Page, audioWsUrl: string, signal: AbortSignal): Promise<void>;
}
