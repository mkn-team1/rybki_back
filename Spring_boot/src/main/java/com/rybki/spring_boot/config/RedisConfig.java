package com.rybki.spring_boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;
    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        final LettuceConnectionFactory factory = new LettuceConnectionFactory(redisHost, redisPort);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(final RedisConnectionFactory connectionFactory) {
        final RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Создаем ObjectMapper для Redis - СОХРАНЯЕТ @JsonIgnore поля
        final ObjectMapper redisObjectMapper = createRedisObjectMapper();

        final GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * ObjectMapper для Redis - сохраняет все поля, включая отмеченные @JsonIgnore
     * Использует addMixIn для переопределения поведения @JsonIgnore
     */
    private ObjectMapper createRedisObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Переопределяем @JsonIgnore для класса Idea через mixin
        mapper.addMixIn(com.rybki.spring_boot.model.domain.redis.Idea.class, IdeaRedisSerializationMixin.class);
        
        mapper.activateDefaultTyping(
            mapper.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL
        );
        
        return mapper;
    }

    /**
     * Mixin для переопределения @JsonIgnore на sets при сохранении в Redis
     * Позволяет сохранять likesClientsSet и dislikesClientsSet в Redis
     */
    public abstract static class IdeaRedisSerializationMixin {
        // Удаляем @JsonIgnore из sets - они будут сохраняться в Redis
        @com.fasterxml.jackson.annotation.JsonProperty
        public java.util.Set<String> likesClientsSet;

        @com.fasterxml.jackson.annotation.JsonProperty
        public java.util.Set<String> dislikesClientsSet;
    }
}
