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

  let activeCleanup: (() => void) | null = null;

  let ws: WebSocket | null = null;

  function ensureSocket() {
      if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return ws;
      ws = new WebSocket(audioWsUrl);
      ws.binaryType = "arraybuffer";
      ws.onopen = () => {
          log("WS Open");
          ws!.send(JSON.stringify({ type: "start", clientId: "123", eventId: "456" }));
      };
      ws.onerror = (e) => log("WS Error", e);
      return ws;
  }

  function startWebAudio(stream: MediaStream) {
    log("New audio track detected! ID:", stream.id);

    if (activeCleanup) {
        log("Stopping previous track processing...");
        activeCleanup();
        activeCleanup = null;
    }

    try {
      const AudioCtx = (window as any).AudioContext || (window as any).webkitAudioContext;
      const audioCtx = new AudioCtx();
      const source = audioCtx.createMediaStreamSource(stream);
      const processor = audioCtx.createScriptProcessor(4096, 1, 1);

      const socket = ensureSocket(); 

      processor.onaudioprocess = (ev: AudioProcessingEvent) => {
        if (!socket || socket.readyState !== WebSocket.OPEN) return;

        const input = ev.inputBuffer.getChannelData(0);
        const resampled = resampleTo16k(input, audioCtx.sampleRate);
        const pcm16 = floatTo16BitPCM(resampled);
        try {
            socket.send(pcm16.buffer);
        } catch(e) {}
      };

      source.connect(processor);
      processor.connect(audioCtx.destination);

      activeCleanup = () => {
          log(`Cleaning up track: ${stream.id}`);
          try { processor.disconnect(); } catch {}
          try { source.disconnect(); } catch {}
          try { if(audioCtx.state !== 'closed') audioCtx.close(); } catch {}
      };

      stream.getAudioTracks()[0].addEventListener("ended", () => {
          log("Track ended naturally");
          if (activeCleanup) activeCleanup(); 
      });

    } catch (e) {
      log("Failed to start pipeline", e);
    }
  }

  (window as any).RTCPeerConnection = function () {
    const pc = new OrigPC(...arguments);
    pc.addEventListener("track", function (ev: any) {
      
      if (ev.track && ev.track.kind === "audio" && ev.streams && ev.streams[0]) {
        startWebAudio(ev.streams[0]);
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
