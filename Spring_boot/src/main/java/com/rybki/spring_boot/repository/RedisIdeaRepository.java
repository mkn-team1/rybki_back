package com.rybki.spring_boot.repository;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.rybki.spring_boot.model.domain.redis.Idea;
import com.rybki.spring_boot.model.domain.redis.IdeaStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisIdeaRepository {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void saveIdea(final Idea idea) {
        final String ideaKey = RedisKeys.ideaKey(idea.getIdeaId());
        redisTemplate.opsForValue().set(ideaKey, idea);

        // Добавляем в pending ideas события
        final String pendingKey = RedisKeys.eventPendingIdeasKey(idea.getEventId());
        redisTemplate.opsForSet().add(pendingKey, idea.getIdeaId());
    }

    public Optional<Idea> findIdeaById(final String ideaId) {
        final String key = RedisKeys.ideaKey(ideaId);
        return Optional.ofNullable((Idea) redisTemplate.opsForValue().get(key));
    }

    public void moveIdeaToAccepted(final String ideaId, final String eventId) {
        final Idea idea = findIdeaById(ideaId).orElseThrow();
        idea.setStatus(IdeaStatus.ACCEPTED);

        final String ideaKey = RedisKeys.ideaKey(ideaId);
        redisTemplate.opsForValue().set(ideaKey, idea);

        // Перемещаем между sets
        final String pendingKey = RedisKeys.eventPendingIdeasKey(eventId);
        final String acceptedKey = RedisKeys.eventAcceptedIdeasKey(eventId);

        redisTemplate.opsForSet().remove(pendingKey, ideaId);
        redisTemplate.opsForSet().add(acceptedKey, ideaId);
    }

    public Set<String> getPendingIdeas(final String eventId) {
        final String key = RedisKeys.eventPendingIdeasKey(eventId);
        return redisTemplate.opsForSet().members(key).stream()
            .map(Object::toString)
            .collect(Collectors.toSet());
    }

    public Set<String> getAcceptedIdeas(final String eventId) {
        final String key = RedisKeys.eventAcceptedIdeasKey(eventId);
        return redisTemplate.opsForSet().members(key).stream()
            .map(Object::toString)
            .collect(Collectors.toSet());
    }
}
