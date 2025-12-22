package com.rybki.spring_boot.repository;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import com.rybki.spring_boot.model.domain.redis.Idea;
import com.rybki.spring_boot.model.domain.redis.IdeaStatus;

@Repository
public class RedisIdeaRepository {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void saveIdea(final Idea idea) {
        final String ideaKey = RedisKeys.ideaKey(idea.getIdeaId());
        redisTemplate.opsForValue().set(ideaKey, idea);

        // Сохраняем Sets голосов отдельно в Redis SET
        final String likesKey = RedisKeys.ideaLikesKey(idea.getIdeaId());
        final String dislikesKey = RedisKeys.ideaDislikesKey(idea.getIdeaId());
        
        // Очищаем старые значения
        redisTemplate.delete(likesKey);
        redisTemplate.delete(dislikesKey);
        
        // Добавляем новые clientIds в Redis SETs
        if (idea.getLikesClientsSet() != null && !idea.getLikesClientsSet().isEmpty()) {
            redisTemplate.opsForSet().add(likesKey, idea.getLikesClientsSet().toArray());
        }
        if (idea.getDislikesClientsSet() != null && !idea.getDislikesClientsSet().isEmpty()) {
            redisTemplate.opsForSet().add(dislikesKey, idea.getDislikesClientsSet().toArray());
        }

        // Добавляем в pending ideas события
        final String pendingKey = RedisKeys.eventPendingIdeasKey(idea.getEventId());
        redisTemplate.opsForSet().add(pendingKey, idea.getIdeaId());
    }

    public Optional<Idea> findIdeaById(final String ideaId) {
        final String key = RedisKeys.ideaKey(ideaId);
        Idea idea = (Idea) redisTemplate.opsForValue().get(key);
        
        if (idea != null) {
            // Загружаем Sets голосов из Redis
            final String likesKey = RedisKeys.ideaLikesKey(ideaId);
            final String dislikesKey = RedisKeys.ideaDislikesKey(ideaId);
            
            Set<Object> likesObjects = redisTemplate.opsForSet().members(likesKey);
            Set<Object> dislikesObjects = redisTemplate.opsForSet().members(dislikesKey);
            
            // Конвертируем Objects в Strings
            if (likesObjects != null && !likesObjects.isEmpty()) {
                idea.setLikesClientsSet(likesObjects.stream()
                    .map(Object::toString)
                    .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>()))));
            }
            if (dislikesObjects != null && !dislikesObjects.isEmpty()) {
                idea.setDislikesClientsSet(dislikesObjects.stream()
                    .map(Object::toString)
                    .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>()))));
            }
        }
        
        return Optional.ofNullable(idea);
    }

    public void moveIdeaToAccepted(final String ideaId, final String eventId) {
        final Idea idea = findIdeaById(ideaId).orElseThrow();
        idea.setStatus(IdeaStatus.GOLDEN);

        final String ideaKey = RedisKeys.ideaKey(ideaId);
        redisTemplate.opsForValue().set(ideaKey, idea);

        // Перемещаем между sets
        final String pendingKey = RedisKeys.eventPendingIdeasKey(eventId);
        final String acceptedKey = RedisKeys.eventAcceptedIdeasKey(eventId);

        redisTemplate.opsForSet().remove(pendingKey, ideaId);
        redisTemplate.opsForSet().add(acceptedKey, ideaId);
    }

    public void moveIdeaToRejected(final String ideaId, final String eventId) {
        moveIdeaToStatus(ideaId, eventId, IdeaStatus.REJECTED);
    }

    private void moveIdeaToStatus(
        final String ideaId,
        final String eventId,
        final IdeaStatus status
    ) {
        final Idea idea = findIdeaById(ideaId).orElseThrow();
        idea.setStatus(IdeaStatus.LOCAL);

        final String ideaKey = RedisKeys.ideaKey(ideaId);
        redisTemplate.opsForValue().set(ideaKey, idea);

        final String pendingKey = RedisKeys.eventPendingIdeasKey(eventId);

        redisTemplate.opsForSet().remove(pendingKey, ideaId);

        String targetKey = switch (status) {
            case ACCEPTED -> RedisKeys.eventAcceptedIdeasKey(eventId);
            case REJECTED -> RedisKeys.eventRejectedIdeasKey(eventId);
            default -> throw new IllegalStateException("Unsupported status: " + status);
        };

        redisTemplate.opsForSet().add(targetKey, ideaId);
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

    public Set<String> getRejectedIdeas(final String eventId) {
        final String key = RedisKeys.eventRejectedIdeasKey(eventId);
        return redisTemplate.opsForSet().members(key).stream()
            .map(Object::toString)
            .collect(Collectors.toSet());
    }

    /**
     * Получить все LOCAL идеи для конкретной конференции
     */
    public Set<String> getLocalIdeasForConference(final String eventId, final String conferenceId) {
        final Set<String> pendingIds = getPendingIdeas(eventId);
        return pendingIds.stream()
            .map(this::findIdeaById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(idea -> idea.getStatus() == IdeaStatus.LOCAL && conferenceId.equals(idea.getConferenceId()))
            .map(Idea::getIdeaId)
            .collect(Collectors.toSet());
    }

    /**
     * Получить все GLOBAL и GOLDEN идеи для Event
     */
    public Set<String> getGlobalAndGoldenIdeas(final String eventId) {
        final Set<String> pendingIds = getPendingIdeas(eventId);
        final Set<String> acceptedIds = getAcceptedIdeas(eventId);
        
        return java.util.stream.Stream.concat(pendingIds.stream(), acceptedIds.stream())
            .map(this::findIdeaById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(idea -> idea.getStatus() == IdeaStatus.GLOBAL || idea.getStatus() == IdeaStatus.GOLDEN)
            .map(Idea::getIdeaId)
            .collect(Collectors.toSet());
    }
}
