package com.rybki.spring_boot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rybki.spring_boot.util.Base64Util;
import com.rybki.spring_boot.websocket.SttWebSocketClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class SttRoutingService {

    private final SttWebSocketClient sttClient;
    private final ObjectMapper objectMapper;

    public SttRoutingService(SttWebSocketClient sttClient) {
        this.sttClient = sttClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Пересылаем PCM16 байты на STT
     */
    public void forwardAudio(String clientId, String eventId, byte[] pcmChunk) {
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
    }

    /**
     * Отправка события окончания аудио
     */
    public void notifyEnd(String clientId, String eventId) {
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
    }
}
