package com.rybki.spring_boot.service;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.rybki.spring_boot.exception.BadRequestException;
import com.rybki.spring_boot.exception.NotFoundException;
import com.rybki.spring_boot.model.domain.api.bot.removed.RemovedBotRequest;
import com.rybki.spring_boot.model.domain.api.bot.removed.RemovedBotResponse;
import com.rybki.spring_boot.model.domain.api.bot.started.StartedBotRequest;
import com.rybki.spring_boot.model.domain.api.bot.started.StartedBotResponse;
import com.rybki.spring_boot.model.domain.redis.Event;
import com.rybki.spring_boot.repository.RedisEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotService {

        private final BotKafkaService botKafkaService;

        // /*
        // * private final ClientNotificationService clientNotificationService;
        // *
        // * // Вспомогательный класс для хранения информации о боте
        // *
        // * @Data
        // * private static class BotInfo {
        // * private final String conferenceId;
        // * private final String eventId;
        // * }
        // *
        // * // Хранение связи: botId -> BotInfo (conferenceId, eventId)
        // * private final Map<String, BotInfo> botRegistry = new ConcurrentHashMap<>();
        // *
        // * /**
        // * Регистрирует связь между botId, conferenceId и eventId
        // */
        // public void registerBot(final String botId, final String conferenceId, final
        // String eventId) {
        // botRegistry.put(botId, new BotInfo(conferenceId, eventId));
        // log.info("📝 [BOT-SERVICE] Registered bot: botId={}, conferenceId={},
        // eventId={}", botId, conferenceId,
        // eventId);
        // }

        // /**
        // * Получает conferenceId по botId
        // */
        // public String getConferenceId(final String botId) {
        // final BotInfo info = botRegistry.get(botId);
        // return info != null ? info.getConferenceId() : null;
        // }

        // /**
        // * Получает eventId по botId
        // */
        // public String getEventId(final String botId) {
        // final BotInfo info = botRegistry.get(botId);
        // return info != null ? info.getEventId() : null;
        // }

        // /**
        // * Удаляет связь botId -> BotInfo
        // */
        // public void unregisterBot(final String botId) {
        // final BotInfo info = botRegistry.remove(botId);
        // if (info != null) {
        // log.info("🗑️ [BOT-SERVICE] Unregistered bot: botId={}, conferenceId={},
        // eventId={}", botId,
        // info.getConferenceId(), info.getEventId());
        // }
        // }

        // public Mono<Void> connectBot(final String conferenceId, final String eventId,
        // final String botId) {
        // log.info("🤖 [BOT-SERVICE] Connecting bot: conferenceId={}, eventId={},
        // botId={}", conferenceId,
        // eventId,
        // botId);
        // registerBot(botId, conferenceId, eventId);
        // return clientNotificationService.botConnected(conferenceId, eventId, botId)
        // .doOnSuccess(v -> log.info("✅ [BOT-SERVICE] Bot connected: botId={}",
        // botId));
        // }

        // public Mono<Void> disconnectBot(final String conferenceId, final String
        // eventId,
        // final String botId) {
        // log.info("🤖 [BOT-SERVICE] Disconnecting bot: conferenceId={}, eventId={},
        // botId={}",
        // conferenceId, eventId, botId);
        // unregisterBot(botId);
        // return clientNotificationService.botDisconnected(conferenceId, eventId,
        // botId)
        // .doOnSuccess(v -> log.info("✅ [BOT-SERVICE] Bot disconnected: botId={}",
        // botId));

        // TODO: yet not used private final ClientNotificationService
        // clientNotificationService;
        private final RedisEventRepository eventRepository;
        private final SessionService sessionService;

        private final ClientNotificationService clientNotificationService;

        public Mono<Void> connectBot(final String conferenceId, final String eventId, final String botId) {
                log.info("🤖 [BOT-SERVICE] Connecting bot: conferenceId={}, eventId={}, botId={}", conferenceId,
                                eventId,
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

        public CreateBotResponse createBot(CreateBotRequest request) {
                final String clientId = request.getClientId();
                final String eventId = request.getEventId();

                // TODO: use this
                Event event = eventRepository.findEventById(eventId)
                                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

                if (!eventRepository.isParticipant(eventId, conferenceId)) {
                        throw new BadRequestException("Client is not a participant of the event: " + eventId);
                }

                // TODO: дописать реализацию создания бота

                // if (!BotTaskProducer.validateUrl(meetingUrl)) {
                // throw new UnprocessableEntityException("Invalid meeting URL");
                // }

                final String botId = UUID.randomUUID().toString();

                botKafkaService.sendConnectBotCommand(botId, clientId, eventId);

                log.info("Bot created: botId={}, clientId={}, eventId={}", botId, clientId, eventId);

                return CreateBotResponse.builder().build();
        }

        public StartedBotResponse handleBotStarted(String botId, StartedBotRequest request) {
                // Получаем clientId для бота
                Optional<String> clientOpt = Optional.ofNullable(sessionService.getClientForBot(botId));
                clientOpt.ifPresent(clientId -> {
                        // TODO: clientNotificationService.notifyBotStarted(clientId, botId);
                        log.info("Bot started: botId={}, clientId={}", botId, clientId);
                });

                return StartedBotResponse.builder().build();
        }

        public RemovedBotResponse handleBotRemoved(String botId, RemovedBotRequest request) {
                // Получаем WS-сессию бота и отправляем команду leave
                WebSocketSession botSession = sessionService.getBotSession(botId);
                if (botSession != null && botSession.isOpen()) {
                        botSession.send(Mono.just(botSession.textMessage("{\"type\":\"leave\"}"))).subscribe();
                }

                // Получаем клиент, связанный с ботом
                Optional<String> clientOpt = Optional.ofNullable(sessionService.getClientForBot(botId));
                // TODO: clientOpt.ifPresent(clientId ->
                // clientNotificationService.notifyBotRemoved(clientId, botId));

                // Убираем связь botId ↔ clientId и WS-сессию
                sessionService.unregisterBot(botId);

                log.info("Bot removed: botId={}", botId);
                return RemovedBotResponse.builder().build();
        }

        public void handleClientLeave(final String eventId, final String clientId) {

                // TODO: use event data
                Event event = eventRepository.findEventById(eventId)
                                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

                if (!eventRepository.isParticipant(eventId, conferenceId)) {
                        throw new BadRequestException("Client is not a participant of the event: " + eventId);
                }

                // Получаем связанного бота и удаляем его
                String botId = sessionService.getBotForClient(clientId);
                if (botId != null) {
                        removeBot(botId);
                }
        }

        private void removeBot(String botId) {
                WebSocketSession botSession = sessionService.getBotSession(botId);
                if (botSession != null && botSession.isOpen()) {
                        botSession.send(Mono.just(botSession.textMessage("{\"type\":\"leave\"}"))).subscribe();
                }
                sessionService.unregisterBot(botId);
                log.info("Removed bot: botId={}", botId);

        }

}
