package com.rybki.spring_boot.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotService {

        private final SessionService sessionService;
        private final BotKafkaService botKafkaService;
        private final ClientNotificationService clientNotificationService;
        private final AudioDumpService audioDumpService;

        public Mono<Void> createBot(final String conferenceId, final String meetingUrl, final String platform) {
            final String botId = UUID.randomUUID().toString();
            log.info("🤖 [BOT-SERVICE] Creating bot: conferenceId={}, eventId={}, botId={}",
                            conferenceId, meetingUrl, botId);

            sessionService.linkClientAndBot(conferenceId, botId);
            return botKafkaService.sendConnectBotCommand(botId, meetingUrl, platform)
                    .doOnSuccess(v -> log.info("✅ [BOT-SERVICE] Bot created: botId={}", botId));
        }
        
        public Mono<Void> disconnectBot(final String conferenceId) {
            final String botId = sessionService.getBotForClient(conferenceId);
            if (botId == null) {
                log.warn("❌ [BOT-SERVICE] No bot linked to conferenceId={}, cannot disconnect", conferenceId);
                return Mono.empty();
            }
            return removeBot(botId);
        }
        
        private Mono<Void> removeBot(final String botId) {
            
            WebSocketSession botSession = sessionService.getBotSession(botId);

            if (botSession == null || !botSession.isOpen()) {
                log.warn("🤖 [BOT-SERVICE] No open WebSocket session for botId={}, cannot send leave", botId);
                return Mono.empty();
            }
            return botSession.send(Mono.just(botSession.textMessage("{\"type\":\"leave\"}")))
                            .doOnSubscribe(s ->
                                log.info("🤖 [BOT-SERVICE] Sending leave to botId={}", botId)
                            )
                            .doOnError(e ->
                                log.error("🤖 [BOT-SERVICE] Failed to send leave to botId={}", botId, e)
                            )
                            .then();
            
        }

        public Mono<Void> handleBotStarted(final String botId) {
            String conferenceId = sessionService.getClientForBot(botId);
    
            if (conferenceId == null) {
                return Mono.empty();
            }
            return sessionService.getClientSession(conferenceId).flatMap(cs -> {
                return audioDumpService.start(cs.getSession().getId(), conferenceId, cs.getEventId());
            }).then(clientNotificationService.botConnected(conferenceId, botId));
        }

        public Mono<Void> handleBotRemoved(final String botId) {
            String conferenceId = sessionService.getClientForBot(botId);

            return sessionService.getClientSession(conferenceId).flatMap(cs -> {
                return audioDumpService.stop(cs.getSession().getId())
                .then(sessionService.unregisterBot(botId))
                .then(clientNotificationService.botDisconnected(conferenceId, botId));
            });
        }
}
