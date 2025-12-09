package com.rybki.spring_boot.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/*
Пока просто заглушка
*/
@Service
@RequiredArgsConstructor
@Slf4j
public class BotService {

    private final ClientNotificationService clientNotificationService;

    public Mono<Void> connectBot(final String conferenceId, final String eventId, final String botId) {
        log.info("🤖 [BOT-SERVICE] Connecting bot: conferenceId={}, eventId={}, botId={}", conferenceId, eventId,
                botId);
        return clientNotificationService.broadcastBotConnected(conferenceId, eventId, botId)
                .doOnSuccess(v -> log.info("✅ [BOT-SERVICE] Bot connected: botId={}", botId));
    }

    public Mono<Void> disconnectBot(final String conferenceId, final String eventId,
            final String botId) {
        log.info("🤖 [BOT-SERVICE] Disconnecting bot: conferenceId={}, eventId={}, botId={}",
                conferenceId, eventId, botId);
        return clientNotificationService.broadcastBotDisconnected(conferenceId, eventId, botId)
                .doOnSuccess(v -> log.info("✅ [BOT-SERVICE] Bot disconnected: botId={}", botId));
    }
}
