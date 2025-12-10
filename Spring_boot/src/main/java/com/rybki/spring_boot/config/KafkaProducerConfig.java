package com.rybki.spring_boot.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

/**
 * Конфигурация Kafka Producer для отправки команд на регистрацию ботов.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public SenderOptions<String, String> senderOptions() {
        final Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        return SenderOptions.create(props);
    }

    @Bean
    public KafkaSender<String, String> kafkaSender(final SenderOptions<String, String> senderOptions) {
        return KafkaSender.create(senderOptions);
    }
}
