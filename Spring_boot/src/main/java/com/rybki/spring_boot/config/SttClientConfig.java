package com.rybki.spring_boot.config;

import java.time.Duration;

import com.rybki.spring_boot.service.SttResponseHandler;
import com.rybki.spring_boot.websocket.SttWebSocketClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@Slf4j
public class SttClientConfig {
    @Value("${stt.url}")
    private String sttUrl;

    @Bean
    public SttWebSocketClient sttWebSocketClient(final SttResponseHandler responseHandler) {
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
