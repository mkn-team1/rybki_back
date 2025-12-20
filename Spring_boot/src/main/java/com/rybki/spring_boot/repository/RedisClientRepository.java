package com.rybki.spring_boot.repository;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Репозиторий для сохранения соответствия clientId -> conferenceId
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisClientRepository {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Сохранить соответствие clientId -> conferenceId
     */
    public void saveClientConference(final String clientId, final String conferenceId, final String eventId) {
        try {
            final String key = RedisKeys.clientSessionKey(clientId);
            final ClientConferenceMapping mapping = new ClientConferenceMapping(conferenceId, eventId);
            redisTemplate.opsForValue().set(key, mapping);
            log.debug("✅ Saved clientId={} -> conferenceId={}, eventId={}", clientId, conferenceId, eventId);
        } catch (final Exception e) {
            log.error("❌ Failed to save client conference mapping: clientId={}", clientId, e);
        }
    }

    /**
     * Получить conferenceId и eventId по clientId
     */
    public Optional<ClientConferenceMapping> getClientConference(final String clientId) {
        try {
            final String key = RedisKeys.clientSessionKey(clientId);
            final Object result = redisTemplate.opsForValue().get(key);
            if (result instanceof ClientConferenceMapping) {
                ClientConferenceMapping mapping = (ClientConferenceMapping) result;
                log.debug("✅ Found clientId={} -> conferenceId={}, eventId={}", 
                        clientId, mapping.conferenceId, mapping.eventId);
                return Optional.of(mapping);
            }
            log.warn("❌ No mapping found for clientId={}", clientId);
            return Optional.empty();
        } catch (final Exception e) {
            log.error("❌ Failed to get client conference mapping: clientId={}", clientId, e);
            return Optional.empty();
        }
    }

    /**
     * Удалить соответствие clientId
     */
    public void deleteClientConference(final String clientId) {
        try {
            final String key = RedisKeys.clientSessionKey(clientId);
            redisTemplate.delete(key);
            log.debug("✅ Deleted clientId={} from Redis", clientId);
        } catch (final Exception e) {
            log.error("❌ Failed to delete client conference mapping: clientId={}", clientId, e);
        }
    }

    /**
     * DTO для хранения в Redis
     */
    public static class ClientConferenceMapping {
        public String conferenceId;
        public String eventId;

        public ClientConferenceMapping(String conferenceId, String eventId) {
            this.conferenceId = conferenceId;
            this.eventId = eventId;
        }

        // For Redis serialization
        public ClientConferenceMapping() {}
    }
}
