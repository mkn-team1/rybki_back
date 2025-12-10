export type PlatformType = "kontur_talk"

export interface JoinConferenceTask {
  botId: string;
  meetingUrl: string;
  platform: PlatformType;
}
