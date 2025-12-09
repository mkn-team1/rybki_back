package com.rybki.spring_boot.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotService {

    // TODO: yet not used private final ClientNotificationService clientNotificationService;
    private final RedisEventRepository eventRepository;
    private final SessionService sessionService;

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
        //     throw new UnprocessableEntityException("Invalid meeting URL");
        // }

        final String botId = UUID.randomUUID().toString();


        // clientBotService.registerBotForClient(clientId, botId);

        // final CreateBotTask createBotTask = botTaskProducer.createBotTask(botId, meetingUrl);
        // botTaskProducer.sendCreateBotTask(createBotTask); или kafkaRepository.sendCreateBotTask(createBotTask);

        // Связь clientId ↔ botId в SessionService (будет установлена при WS подключении)
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
        // TODO: clientOpt.ifPresent(clientId -> clientNotificationService.notifyBotRemoved(clientId, botId));

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
