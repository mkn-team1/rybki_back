package com.rybki.spring_boot.config;

import com.rybki.spring_boot.websocket.SttWebSocketClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class SttClientConfig {

    @Bean
    public SttWebSocketClient sttWebSocketClient() {
        String sttUrl = "ws://localhost:8081/ws/stt"; // поменять на реальный URL STT
        Duration timeout = Duration.ofSeconds(10);
        Duration reconnectInitial = Duration.ofSeconds(1);
        Duration reconnectMax = Duration.ofSeconds(30);

        SttWebSocketClient client = new SttWebSocketClient(sttUrl, timeout, reconnectInitial, reconnectMax);

        // Простейший коллбек для логирования сообщений от STT
        client.setMessageHandler(msg -> log.info("Received from STT: {}", msg));

        // Стартуем автоматически при старте Spring
        client.start();

        return client;
    }
}
