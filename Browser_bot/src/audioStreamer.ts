import { Page } from "playwright";
import { logger } from "./logger";

/**
 * ВНИМАНИЕ: эта функция выполняется ВНУТРИ страницы браузера.
 */
function browserSideWebRtcAudio(params: { audioWsUrl: string; botId: string }) {
  const { audioWsUrl, botId } = params;

  function log(...args: any[]) {
    try { console.log("[audio-bot]", ...args); } catch (_) {}
  }

  const OrigPC = (window as any).RTCPeerConnection || (window as any).webkitRTCPeerConnection;
  if (!OrigPC) return;
  if ((window as any).__audioBotPatched) return;
  (window as any).__audioBotPatched = true;

  function floatTo16BitPCM(float32: Float32Array): Int16Array {
    const out = new Int16Array(float32.length);
    for (let i = 0; i < float32.length; i++) {
      const s = Math.max(-1, Math.min(1, float32[i]));
      out[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
    }
    return out;
  }
  function resampleTo16k(input: Float32Array, inSampleRate: number): Float32Array {
    if (inSampleRate === 16000) return input;
    const ratio = inSampleRate / 16000;
    const newLength = Math.floor(input.length / ratio);
    const output = new Float32Array(newLength);
    for (let i = 0; i < newLength; i++) {
        const idx = i * ratio;
        const t = idx - Math.floor(idx);
        output[i] = input[Math.floor(idx)] * (1 - t) + input[Math.min(Math.floor(idx) + 1, input.length - 1)] * t;
    }
    return output;
  }

  let globalCtx: AudioContext | null = null;
  let globalProcessor: ScriptProcessorNode | null = null;
  let globalSocket: WebSocket | null = null;
  // Храним подключенные треки, чтобы не добавлять дубли
  const connectedTrackIds = new Set<string>();

  function ensureGlobalPipeline() {
     if (globalCtx && globalCtx.state !== 'closed') {
         // Проверяем, жив ли сокет
         if (globalSocket && (globalSocket.readyState === WebSocket.CLOSED || globalSocket.readyState === WebSocket.CLOSING)) {
             log("Socket closed, recreating...");
             globalSocket = null; // Пересоздастся ниже
         }
     } else {
         // Инициализация контекста
         const AudioCtx = (window as any).AudioContext || (window as any).webkitAudioContext;
         globalCtx = new AudioCtx();
         log("Created Global AudioContext", globalCtx!.sampleRate);
         
         // Создаем процессор (Микшер -> Processor -> Destination)
         // Буфер 4096 ~ 90мс задержки, можно уменьшить до 2048
         globalProcessor = globalCtx!.createScriptProcessor(4096, 1, 1);
         globalProcessor.connect(globalCtx!.destination);
         
         globalProcessor.onaudioprocess = (ev) => {
             if (!globalSocket || globalSocket.readyState !== WebSocket.OPEN) return;
             
             const input = ev.inputBuffer.getChannelData(0);

             const resampled = resampleTo16k(input, globalCtx!.sampleRate);
             const pcm16 = floatTo16BitPCM(resampled);
             try { globalSocket.send(pcm16.buffer); } catch (e) {}
         };
     }

     if (!globalSocket) {
         globalSocket = new WebSocket(audioWsUrl);
         globalSocket.binaryType = "arraybuffer";
         globalSocket.onopen = () => log("WS Open");

         globalSocket.onmessage = (event) => {
            try {
                const msg = JSON.parse(event.data);

                if (msg.type === 'leave') {
                    // Пишем специальный лог, который перехватит Playwright
                    console.log("[BOT_COMMAND:LEAVE]"); 
                }
            } catch (e) {}
          }

         globalSocket.onerror = (e) => log("WS Error", e);
         globalSocket.onclose = (e) => log("WS Closed", e.code);
     }
     
     return { ctx: globalCtx!, processor: globalProcessor! };
  }

  function connectTrack(track: MediaStreamTrack, streamId: string) {
     if (connectedTrackIds.has(track.id)) return;
     connectedTrackIds.add(track.id);
     
     log(`Connecting new track: ${track.id} (stream: ${streamId})`);
     
     try {
         const { ctx, processor } = ensureGlobalPipeline();
         
         // Создаем поток только из этого трека
         const stream = new MediaStream([track]);
         const source = ctx.createMediaStreamSource(stream);
         
         // Подключаем к глобальному процессору (автоматическое микширование)
         source.connect(processor);
         
         // Следим за окончанием трека
         track.addEventListener('ended', () => {
             log(`Track ended: ${track.id}`);
             try { source.disconnect(); } catch {}
             connectedTrackIds.delete(track.id);
         });
         
     } catch (e) {
         log("Failed to connect track", e);
     }
  }

  // Перехват RTCPeerConnection
  (window as any).RTCPeerConnection = function () {
    const pc = new OrigPC(...arguments);
    
    pc.addEventListener("track", function (ev: any) {
      if (ev.track && ev.track.kind === "audio") {
        // Подключаем ВСЕ входящие аудио-треки
        connectTrack(ev.track, ev.streams[0]?.id);
      }
    });
    
    return pc;
  };
}

export async function installWebRtcAudioHook(
  page: Page,
  audioWsUrl: string,
  botId: string
): Promise<void> {
  await page.addInitScript(browserSideWebRtcAudio, { audioWsUrl, botId });
  logger.info({ botId }, "WebRTC audio hook injected");
}
