package com.rybki.spring_boot.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotService {

        private final ClientNotificationService clientNotificationService;

        // Вспомогательный класс для хранения информации о боте
        @Data
        private static class BotInfo {
                private final String conferenceId;
                private final String eventId;
        }

        // Хранение связи: botId -> BotInfo (conferenceId, eventId)
        private final Map<String, BotInfo> botRegistry = new ConcurrentHashMap<>();

        /**
         * Регистрирует связь между botId, conferenceId и eventId
         */
        public void registerBot(final String botId, final String conferenceId, final String eventId) {
                botRegistry.put(botId, new BotInfo(conferenceId, eventId));
                log.info("📝 [BOT-SERVICE] Registered bot: botId={}, conferenceId={}, eventId={}", botId, conferenceId,
                                eventId);
        }

        /**
         * Получает conferenceId по botId
         */
        public String getConferenceId(final String botId) {
                final BotInfo info = botRegistry.get(botId);
                return info != null ? info.getConferenceId() : null;
        }

        /**
         * Получает eventId по botId
         */
        public String getEventId(final String botId) {
                final BotInfo info = botRegistry.get(botId);
                return info != null ? info.getEventId() : null;
        }

        /**
         * Удаляет связь botId -> BotInfo
         */
        public void unregisterBot(final String botId) {
                final BotInfo info = botRegistry.remove(botId);
                if (info != null) {
                        log.info("🗑️ [BOT-SERVICE] Unregistered bot: botId={}, conferenceId={}, eventId={}", botId,
                                        info.getConferenceId(), info.getEventId());
                }
        }

        public Mono<Void> connectBot(final String conferenceId, final String eventId, final String botId) {
                log.info("🤖 [BOT-SERVICE] Connecting bot: conferenceId={}, eventId={}, botId={}", conferenceId,
                                eventId,
                                botId);
                registerBot(botId, conferenceId, eventId);
                return clientNotificationService.botConnected(conferenceId, eventId, botId)
                                .doOnSuccess(v -> log.info("✅ [BOT-SERVICE] Bot connected: botId={}", botId));
        }

        public Mono<Void> disconnectBot(final String conferenceId, final String eventId,
                        final String botId) {
                log.info("🤖 [BOT-SERVICE] Disconnecting bot: conferenceId={}, eventId={}, botId={}",
                                conferenceId, eventId, botId);
                unregisterBot(botId);
                return clientNotificationService.botDisconnected(conferenceId, eventId, botId)
                                .doOnSuccess(v -> log.info("✅ [BOT-SERVICE] Bot disconnected: botId={}", botId));
        }
}
