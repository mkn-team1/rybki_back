package com.rybki.spring_boot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


// На данный момент сервис сохраняет аудио просто в файл
// TODO: Сделать сохранение байтов аудио в отдельном месте

@Slf4j
@Service
public class AudioDumpService {

    // Флаг включения/выключения (можно задать AUDIO_DUMP_ENABLED=true|false)
    @Value("${app.debug.audio-dump.enabled:false}")
    private boolean enabled;

    // Фиксированный путь внутри контейнера
    private static final Path BASE_DIR = Paths.get("/app/audio-dumps");

    // Фиксированный формат потока: 16 kHz / mono / 16-bit PCM
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;

    private final Map<String, Writer> writers = new ConcurrentHashMap<>();

    public Mono<Void> start(String sessionId, String clientId, String eventId) {
        if (!enabled) return Mono.empty();
        return Mono.fromRunnable(() -> {
                try {
                    Files.createDirectories(BASE_DIR.resolve(safe(eventId)).resolve(safe(clientId)));
                    final String ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
                    final Path path = BASE_DIR.resolve(safe(eventId))
                                             .resolve(safe(clientId))
                                             .resolve(ts + "-" + sessionId + ".wav");

                    final Writer w = new Writer(path, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE);
                    w.open();
                    final Writer prev = writers.put(sessionId, w);
                    if (prev != null) {
                        try { prev.close(); } catch (Exception ignore) {}
                    }
                    log.info("AUDIO DUMP STARTED: sessionId={}, clientId={}, eventId={}, sr=16000, path={}", 
                             sessionId, clientId, eventId, path);
                } catch (Exception e) {
                    log.error("Audio dump start failed: sessionId={}", sessionId, e);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    public Mono<Void> append(String sessionId, byte[] pcm) {
        if (!enabled || pcm == null || pcm.length == 0) return Mono.empty();
        return Mono.fromRunnable(() -> {
                final Writer w = writers.get(sessionId);
                if (w == null) return;
                try {
                    w.append(pcm);
                    log.debug("AUDIO DUMP APPEND: sessionId={}, +{} bytes, total={} bytes", 
                              sessionId, pcm.length, w.getDataBytes());
                } catch (Exception e) {
                    log.error("Audio dump append failed: sessionId={}", sessionId, e);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    public Mono<Void> stop(String sessionId) {
        if (!enabled) return Mono.empty();
        return Mono.fromRunnable(() -> {
                final Writer w = writers.remove(sessionId);
                if (w == null) return;
                try {
                    w.close();
                    final double seconds = w.getDataBytes() / (double) (w.sampleRate * w.blockAlign());
                    log.info("AUDIO DUMP STOP: sessionId={}, path={}, totalBytes={}, durationSec={}", 
                             sessionId, w.path, w.getDataBytes(), String.format("%.2f", seconds));
                } catch (Exception e) {
                    log.error("Audio dump stop failed: sessionId={}", sessionId, e);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    private static String safe(String s) {
        return s == null ? "unknown" : s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static final class Writer {
        final Path path;
        final int sampleRate;
        final int channels;
        final int bitsPerSample;

        RandomAccessFile raf;
        long dataBytes;

        Writer(Path path, int sampleRate, int channels, int bitsPerSample) {
            this.path = path;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.bitsPerSample = bitsPerSample;
        }

        void open() throws IOException {
            Files.createDirectories(path.getParent());
            raf = new RandomAccessFile(path.toFile(), "rw");
            raf.setLength(0);
            writeWaveHeader(0); // заглушка, реальные размеры проставим в close()
        }

        void append(byte[] pcm) throws IOException {
            Objects.requireNonNull(raf, "RAF not open");
            raf.seek(raf.length());
            raf.write(pcm);
            dataBytes += pcm.length;
        }

        void close() throws IOException {
            if (raf == null) return;
            writeWaveHeader(dataBytes);
            raf.close();
            raf = null;
        }

        long getDataBytes() { return dataBytes; }

        int blockAlign() {
            return channels * (bitsPerSample / 8);
        }

        int byteRate() {
            return sampleRate * blockAlign();
        }

        private void writeWaveHeader(long dataSize) throws IOException {
            raf.seek(0);
            // RIFF
            raf.writeBytes("RIFF");
            writeLEInt((int) (36 + dataSize));        // ChunkSize
            raf.writeBytes("WAVE");
            // fmt 
            raf.writeBytes("fmt ");
            writeLEInt(16);                            // Subchunk1Size (PCM)
            writeLEShort((short) 1);                   // AudioFormat = 1 (PCM)
            writeLEShort((short) channels);            // NumChannels
            writeLEInt(sampleRate);                    // SampleRate
            writeLEInt(byteRate());                    // ByteRate
            writeLEShort((short) blockAlign());        // BlockAlign
            writeLEShort((short) bitsPerSample);       // BitsPerSample
            // data
            raf.writeBytes("data");
            writeLEInt((int) dataSize);                // Subchunk2Size
        }

        private void writeLEInt(int v) throws IOException {
            raf.write(v & 0xff);
            raf.write((v >> 8) & 0xff);
            raf.write((v >> 16) & 0xff);
            raf.write((v >> 24) & 0xff);
        }

        private void writeLEShort(short v) throws IOException {
            raf.write(v & 0xff);
            raf.write((v >> 8) & 0xff);
        }
    }
}
