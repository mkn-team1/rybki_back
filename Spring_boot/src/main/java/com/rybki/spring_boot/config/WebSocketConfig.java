package com.rybki.spring_boot.config;

import java.util.Map;

import com.rybki.spring_boot.websocket.BotWebSocketHandler;
import com.rybki.spring_boot.websocket.ClientWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {

    private final ClientWebSocketHandler clientWebSocketHandler;
    private final BotWebSocketHandler botWebSocketHandler;

    @Bean
    public SimpleUrlHandlerMapping webSocketMapping() {
        Map<String, WebSocketHandler> urlMap = Map.of(
            "/ws/client/**", clientWebSocketHandler,
            "/ws/bot/**", botWebSocketHandler
        );

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(urlMap);
        mapping.setOrder(10);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
