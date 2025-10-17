package com.rybki.spring_boot.repository;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.rybki.spring_boot.model.domain.redis.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisEventRepository {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void createEvent(final Event event) {
        final String key = RedisKeys.eventKey(event.getEventId());
        redisTemplate.opsForValue().set(key, event);

        // Создаем пустые sets для участников и идей
        redisTemplate.opsForSet().add(RedisKeys.eventParticipantsKey(event.getEventId()), event.getCreatorClientId());
        redisTemplate.opsForSet().add(RedisKeys.eventPendingIdeasKey(event.getEventId()));
        redisTemplate.opsForSet().add(RedisKeys.eventAcceptedIdeasKey(event.getEventId()));
    }

    public Optional<Event> findEventById(final String eventId) {
        final String key = RedisKeys.eventKey(eventId);
        return Optional.ofNullable((Event) redisTemplate.opsForValue().get(key));
    }

    public void addParticipant(final String eventId, final String clientId) {
        final String key = RedisKeys.eventParticipantsKey(eventId);
        redisTemplate.opsForSet().add(key, clientId);
    }

    public Set<String> getParticipants(final String eventId) {
        final String key = RedisKeys.eventParticipantsKey(eventId);
        return redisTemplate.opsForSet().members(key).stream()
            .map(Object::toString)
            .collect(Collectors.toSet());
    }

    public boolean isParticipant(final String eventId, final String clientId) {
        final String key = RedisKeys.eventParticipantsKey(eventId);
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, clientId));
    }
}
