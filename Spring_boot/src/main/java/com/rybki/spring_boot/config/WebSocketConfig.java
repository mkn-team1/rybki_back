package com.rybki.spring_boot.config;

import com.rybki.spring_boot.websocket.ClientWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Collections;

@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {

    private final ClientWebSocketHandler clientWebSocketHandler;

    @Bean
    public SimpleUrlHandlerMapping webSocketMapping() {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Collections.singletonMap("/ws/client", clientWebSocketHandler));
        mapping.setOrder(10); // порядок маппинга
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
