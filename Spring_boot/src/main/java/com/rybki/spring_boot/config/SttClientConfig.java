package com.rybki.spring_boot.config;

import java.time.Duration;

import com.rybki.spring_boot.service.SttResponseHandler;
import com.rybki.spring_boot.websocket.SttWebSocketClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@Slf4j
public class SttClientConfig {

    @Bean
    public SttWebSocketClient sttWebSocketClient(final SttResponseHandler responseHandler) {
        final String sttUrl = "ws://localhost:8081/ws/stt"; // поменять на реальный URL STT
        final Duration timeout = Duration.ofSeconds(10);
        final Duration reconnectInitial = Duration.ofSeconds(1);
        final Duration reconnectMax = Duration.ofSeconds(30);

        final SttWebSocketClient client = new SttWebSocketClient(
            sttUrl,
            timeout,
            reconnectInitial,
            reconnectMax,
            responseHandler
        );

        // Стартуем автоматически при старте Spring
        client.start();

        return client;
    }
}
