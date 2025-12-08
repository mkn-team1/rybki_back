package com.rybki.spring_boot.service;

import java.util.UUID;
import java.util.Map;

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

@Service
@Slf4j
@RequiredArgsConstructor
public class BotService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClientNotificationService clientNotificationService;
    private final RedisEventRepository eventRepository;
    // private final ClientBotService clientBotService;
    // private final KafkaRepository kafkaRepository;
    // private final BotTaskProducer botTaskProducer;

    public void createBot(final String conferenceId, final String eventId, final String meetingUrl) {

        final Event event = eventRepository.findEventById(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found with id: " + eventId));

        if (!eventRepository.isParticipant(eventId, conferenceId)) {
            throw new BadRequestException("Client is not a participant of the event: " + eventId);
        }
        
        // TODO: дописать реализацию создания бота

        // if (!BotTaskProducer.validateUrl(meetingUrl)) {
        //     throw new UnprocessableEntityException("Invalid meeting URL");
        // }

        final String botId = UUID.randomUUID().toString();

        // clientBotService.registerBotForClient(conferenceId, botId);

        // final CreateBotTask createBotTask = botTaskProducer.createBotTask(botId, meetingUrl);
        // botTaskProducer.sendCreateBotTask(createBotTask); или kafkaRepository.sendCreateBotTask(createBotTask);

    }

    public StartedBotResponse handleBotStarted(final String botId, final StartedBotRequest startedBotRequest) {

        // TODO: дописать реализацию обработки старта бота
        
        // if (!clientBotService.isBotRegistered(botId)) {
        //     throw new NotFoundException("Bot not found with id: " + botId);
        // }

        // clientNotificationService.notifyBotStarted(botId);

        return StartedBotResponse.builder().build();
    }

    public RemovedBotResponse handleBotRemoved(final String botId, final RemovedBotRequest removedBotRequest) {

        // TODO: дописать реализацию обработки удаления бота

        // if (!clientBotService.isBotRegistered(botId)) {
        //     throw new NotFoundException("Bot not found with id: " + botId);
        // }

        // clientNotificationService.notifyBotRemoved(botId);

        // clientBotService.unregisterBot(botId);

        return RemovedBotResponse.builder().build();
    }

    public void handleClientLeave(final String eventId, final String conferenceId) {
        final Event event = eventRepository.findEventById(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found with id: " + eventId));

        if (!eventRepository.isParticipant(eventId, conferenceId)) {
            throw new BadRequestException("Client is not a participant of the event: " + eventId);
        }

        // TODO: дописать логику поиска и удаления бота, связанного с клиентом

        // final String botId = clientBotService.getBotIdForClient(conferenceId);
        // if (botId != null) {
        //     removeBot(botId);
        // }

    }

    private void removeBot(final String botId) {

        // TODO: дописать логику удаления бота

        // session = clientBotService.getBotSession(botId);

        // session.send("Some message")

        // clientBotService.unregisterBot(botId);
    }

}
