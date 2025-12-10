package com.rybki.spring_boot.service;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

/**
 * Сервис для отправки команд на регистрацию ботов через Kafka.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotKafkaService {

    private static final String BOT_COMMANDS_TOPIC = "bot-commands";

    private final KafkaSender<String, String> kafkaSender;

    /**
     * Отправка команды на регистрацию бота в Kafka.
     *
     * @param botId      идентификатор бота (генерируется бекендом)
     * @param meetingUrl ссылка на встречу
     * @param platform   платформа (пока только "kontur_talk")
     * @return Mono, завершающийся после отправки
     */
    public Mono<Void> sendConnectBotCommand(final String botId,
            final String meetingUrl,
            final String platform) {
        final String commandJson = String.format(
                "{\"botId\":\"%s\",\"meetingUrl\":\"%s\",\"platform\":\"%s\"}",
                botId, meetingUrl, platform);

        final String key = botId;

        log.info("Sending connect bot command to Kafka: botId={}, meetingUrl={}, platform={}",
                botId, meetingUrl, platform);

        final ProducerRecord<String, String> record = new ProducerRecord<>(BOT_COMMANDS_TOPIC, key, commandJson);
        final SenderRecord<String, String, Void> senderRecord = SenderRecord.create(record, null);

        return kafkaSender.send(Mono.just(senderRecord))
                .next()
                .doOnSuccess(result -> log.info(
                        "Connect bot command sent successfully: botId={}, meetingUrl={}, offset={}",
                        botId, meetingUrl, result.recordMetadata().offset()))
                .doOnError(error -> log.error(
                        "Failed to send connect bot command: botId={}, meetingUrl={}, error={}",
                        botId, meetingUrl, error.getMessage()))
                .then();
    }
}
