package com.rybki.spring_boot.service;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rybki.spring_boot.util.Base64Util;
import com.rybki.spring_boot.websocket.SttWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
@RequiredArgsConstructor
public class SttRoutingService {

    private final SttWebSocketClient sttClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Пересылаем PCM16 байты на STT (реактивно)
     */
    public Mono<Void> forwardAudio(final String conferenceId, final String eventId, final byte[] pcmChunk) {
        return Mono.fromRunnable(() -> {
            try {
                final String audioBase64 = Base64Util.encode(pcmChunk);

                final Map<String, Object> payload = new HashMap<>();
                payload.put("type", "audio");
                payload.put("conferenceId", conferenceId);
                payload.put("eventId", eventId);
                payload.put("audio", audioBase64);

                final String json = objectMapper.writeValueAsString(payload);
                sttClient.sendToStt(json);

                log.debug("📤 Forwarded audio to STT: conferenceId={}, eventId={}, size={} bytes",
                    conferenceId, eventId, pcmChunk.length);

            } catch (JsonProcessingException e) {
                log.error("❌ [STT-ROUTING] Failed to serialize audio JSON: conferenceId={}, eventId={}", conferenceId, eventId, e);
            } catch (Exception e) {
                log.error("❌ [STT-ROUTING] Failed to forward audio to STT: conferenceId={}, eventId={}", conferenceId, eventId, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Отправка события окончания аудио (реактивно)
     */
    public Mono<Void> notifyEnd(final String conferenceId, final String eventId) {
        return Mono.fromRunnable(() -> {
            try {
                final Map<String, Object> payload = new HashMap<>();
                payload.put("type", "disconnect");
                payload.put("conferenceId", conferenceId);
                payload.put("eventId", eventId);

                final String json = objectMapper.writeValueAsString(payload);
                sttClient.sendToStt(json);

                log.info("📤 Sent disconnect to STT for conferenceId={}, eventId={}", conferenceId, eventId);

            } catch (JsonProcessingException e) {
                log.error("Failed to serialize disconnect JSON: conferenceId={}, eventId={}", conferenceId, eventId, e);
            } catch (Exception e) {
                log.error("Failed to notify STT about disconnect: conferenceId={}, eventId={}", conferenceId, eventId, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
