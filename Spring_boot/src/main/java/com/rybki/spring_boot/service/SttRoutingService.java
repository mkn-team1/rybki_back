package com.rybki.spring_boot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rybki.spring_boot.util.Base64Util;
import com.rybki.spring_boot.websocket.SttWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SttRoutingService {

    private final SttWebSocketClient sttClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Пересылаем PCM16 байты на STT (реактивно) */
    public Mono<Void> forwardAudio(String clientId, String eventId, byte[] pcmChunk) {
        return Mono.fromRunnable(() -> {
            try {
                String audioBase64 = Base64Util.encode(pcmChunk);

                Map<String, Object> payload = new HashMap<>();
                payload.put("type", "audio");
                payload.put("clientId", clientId);
                payload.put("eventId", eventId);
                payload.put("audio", audioBase64);

                String json = objectMapper.writeValueAsString(payload);
                sttClient.sendToStt(json);

                log.debug("Forwarded audio to STT: clientId={}, eventId={}, size={} bytes",
                    clientId, eventId, pcmChunk.length);

            } catch (JsonProcessingException e) {
                log.error("Failed to serialize audio JSON: clientId={}, eventId={}", clientId, eventId, e);
            } catch (Exception e) {
                log.error("Failed to forward audio to STT: clientId={}, eventId={}", clientId, eventId, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /** Отправка события окончания аудио (реактивно) */
    public Mono<Void> notifyEnd(String clientId, String eventId) {
        return Mono.fromRunnable(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("type", "disconnect");
                payload.put("clientId", clientId);
                payload.put("eventId", eventId);

                String json = objectMapper.writeValueAsString(payload);
                sttClient.sendToStt(json);

                log.info("Sent disconnect to STT for clientId={}, eventId={}", clientId, eventId);

            } catch (JsonProcessingException e) {
                log.error("Failed to serialize disconnect JSON: clientId={}, eventId={}", clientId, eventId, e);
            } catch (Exception e) {
                log.error("Failed to notify STT about disconnect: clientId={}, eventId={}", clientId, eventId, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
