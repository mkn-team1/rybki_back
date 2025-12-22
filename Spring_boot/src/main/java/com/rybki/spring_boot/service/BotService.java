package com.rybki.spring_boot.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.rybki.spring_boot.exception.BadRequestException;
import com.rybki.spring_boot.exception.InternalServerErrorException;
import com.rybki.spring_boot.exception.UnprocessableEntityException;
import com.rybki.spring_boot.model.domain.Platform;
import com.rybki.spring_boot.model.domain.api.bot.create.CreateBotRequest;
import com.rybki.spring_boot.model.domain.api.bot.create.CreateBotResponse;

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
        private final PlatformParserService platformParserService;

        
        public Mono<Void> disconnectBot(final String conferenceId) {
            log.info("🤖 [BOT-SERVICE] Disconnecting bot for conferenceId={}", conferenceId);
            final String botId = sessionService.getBotForConference(conferenceId);
            if (botId == null) {
                log.warn("❌ [BOT-SERVICE] No bot linked to conferenceId={}, cannot disconnect", conferenceId);
                return Mono.empty();
            }
            return removeBot(botId);
        }
        
        private Mono<Void> removeBot(final String botId) {
            log.info("🤖 [BOT-SERVICE] Removing bot: botId={}", botId);
            WebSocketSession botSession = sessionService.getBotSession(botId);

            if (botSession == null || !botSession.isOpen()) {
                log.warn("🤖 [BOT-SERVICE] No open WebSocket session for botId={}, cannot send leave", botId);
                return handleBotRemoved(botId);
            }
            return botSession.send(Mono.just(botSession.textMessage("{\"type\":\"leave\"}")))
                            .doOnSuccess(s ->
                                log.info("🤖 [BOT-SERVICE] Sending leave to botId={}", botId)
                            )
                            .doOnError(e ->
                                log.error("🤖 [BOT-SERVICE] Failed to send leave to botId={}", botId, e)
                            )
                            .then();
        }

        public Mono<Void> handleBotStarted(final WebSocketSession session, final String botId) {
            String conferenceId = sessionService.getConferenceForBot(botId);
            String eventId = sessionService.getConferenceInfo(conferenceId).block().getEventId();

            if (conferenceId == null || eventId == null) {
                return Mono.empty();
            }
            
            return sessionService.registerBot(botId, session)
            .then(audioDumpService.start(conferenceId, eventId))
            .then(clientNotificationService.botConnected(conferenceId, botId))
            .then(clientNotificationService.sendMicSwitchNotification(conferenceId, false));
        }

        public Mono<Void> handleBotRemoved(final String botId) {
            String conferenceId = sessionService.getConferenceForBot(botId);

            return sessionService.getConferenceInfo(conferenceId).flatMap(cs -> {
                return audioDumpService.stop(cs.getConferenceId())
                .then(clientNotificationService.sendMicSwitchNotification(conferenceId, true))
                .then(sessionService.unregisterBot(botId))
                .then(clientNotificationService.botDisconnected(conferenceId, botId));
            });
        }

        public Boolean isMicMuted(final String botId) {
            return sessionService.isBotMicMuted(botId);
        }

        public Mono<Void> switchMic(final String conferenceId) {
            String botId = sessionService.getBotForConference(conferenceId);
            if (botId == null) {
                log.warn("❌ [BOT-SERVICE] No bot linked to conferenceId={}, cannot switch mic", conferenceId);
                return clientNotificationService.sendMicSwitchNotification(conferenceId, true);
            }
            
            Boolean isMicMuted = sessionService.switchBotMic(botId);

            return clientNotificationService.sendMicSwitchNotification(conferenceId, isMicMuted);
        }

        public CreateBotResponse handleCreateBotRequest(final CreateBotRequest request) {
            Platform platform = platformParserService.parsePlatform(request.getMeetingUrl());
            if (platform == null) {
                throw new BadRequestException("Unsupported platform in meeting URL");
            }

            String existingBotId = sessionService.getBotForConference(request.getConferenceId());
            if (existingBotId != null) {
                return CreateBotResponse.builder().build();
            }

            if (sessionService.getParticipantsCountForConference(request.getConferenceId()) == 0) {
                throw new UnprocessableEntityException("No active client session for the given conference ID");
            }

            String meetingUrl = platformParserService.ensureProtocol(request.getMeetingUrl());

            try {
                String botId = UUID.randomUUID().toString();
                log.info("🤖 [BOT-SERVICE] Creating bot: conferenceId={}, eventId={}, botId={}",
                                request.getConferenceId(), meetingUrl, botId);

                sessionService.linkConferenceAndBot(request.getConferenceId(), botId);

                // Запускаем таймер на очистку, если бот не подключится
                Mono.delay(Duration.ofSeconds(60))
                    .subscribe(v -> {
                        String currentBotId = sessionService.getBotForConference(request.getConferenceId());
                        if (botId.equals(currentBotId) && sessionService.getBotSession(botId) == null) {
                            log.warn("⏰ [BOT-SERVICE] Bot connection timeout: botId={}. Cleaning up session link.", botId);
                            sessionService.unlinkBot(botId);
                        }
                    });

                botKafkaService.sendConnectBotCommand(botId, meetingUrl, platform.getPlatformName())
                        .doOnSuccess(v -> log.info("✅ [BOT-SERVICE] Bot command sent: botId={}", botId))
                        .subscribe(
                            v -> {},
                            e -> {
                                log.error("❌ [BOT-SERVICE] Error sending bot command: {}", e.getMessage());
                                sessionService.unlinkBot(botId);
                            }
                        );
    
                return CreateBotResponse.builder().build();

            } catch (Exception e) {
                log.error("❌ [BOT-SERVICE] Failed to create bot: {}", e.getMessage());
                throw new InternalServerErrorException("Failed to create bot");
            }
        }
}
