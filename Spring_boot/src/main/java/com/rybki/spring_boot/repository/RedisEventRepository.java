package com.rybki.spring_boot.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import com.rybki.spring_boot.model.domain.redis.Event;
import com.rybki.spring_boot.model.domain.redis.EventStatus;
import static com.rybki.spring_boot.repository.RedisKeys.eventKey;
import static com.rybki.spring_boot.repository.RedisKeys.eventParticipantsKey;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Repository
public class RedisEventRepository {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public boolean isRedisConnected() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (final Exception e) {
            log.error("Redis connection failed: {}", e.getMessage());
            return false;
        }
    }

    public void createEvent(final Event event) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            final String key = eventKey(event.getEventId());
            redisTemplate.opsForValue().set(key, event);

            final String participantsKey = eventParticipantsKey(event.getEventId());
            redisTemplate.opsForSet().add(participantsKey, event.getCreatorConferenceId());

            log.debug("Event created in Redis: {}", event.getEventId());
        } catch (final Exception e) {
            log.error("Error creating event in Redis: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to create event in Redis", e);
        }
    }

    public Optional<Event> findEventById(final String eventId) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            final String key = eventKey(eventId);
            final Object result = redisTemplate.opsForValue().get(key);
            if (result == null) {
                return Optional.empty();
            }
            final Event event;
            if (result instanceof Event) {
                event = (Event) result;
                return Optional.ofNullable(event);
            }
            log.error("Expected Event but got: {}", result != null ? result.getClass() : "null");
            throw new RuntimeException("Invalid data");
        } catch (final Exception e) {
            log.error("Error finding event in Redis: {}", eventId, e);
            return Optional.empty();
        }
    }

    // ОБНОВЛЕНИЕ СОБЫТИЯ
    public void updateEvent(final Event event) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            final String key = eventKey(event.getEventId());
            redisTemplate.opsForValue().set(key, event);
            log.debug("Event updated in Redis: {}", event.getEventId());
        } catch (final Exception e) {
            log.error("Error updating event in Redis: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to update event in Redis", e);
        }
    }

    // ОБНОВЛЕНИЕ СТАТУСА СОБЫТИЯ
    public void updateEventStatus(final String eventId, final EventStatus status) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            final Optional<Event> eventOpt = findEventById(eventId);
            if (eventOpt.isPresent()) {
                final Event event = eventOpt.get();
                event.setStatus(status);
                if (status == EventStatus.ENDED) {
                    event.setEndedAt(Instant.now());
                }
                updateEvent(event);
                log.debug("Event status updated: {} -> {}", eventId, status);
            }
        } catch (final Exception e) {
            log.error("Error updating event status: {}", eventId, e);
            throw new RuntimeException("Failed to update event status", e);
        }
    }

    // ПРОВЕРКА СУЩЕСТВОВАНИЯ СОБЫТИЯ
    public boolean eventExists(final String eventId) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            final String key = eventKey(eventId);
            return redisTemplate.hasKey(key);
        } catch (final Exception e) {
            log.error("Error checking event existence: {}", eventId, e);
            return false;
        }
    }

    // ДОБАВЛЕНИЕ УЧАСТНИКА
    public void addParticipant(final String eventId, final String conferenceId) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            final String key = eventParticipantsKey(eventId);
            final Long added = redisTemplate.opsForSet().add(key, conferenceId);
            if (added != null && added > 0) {
                log.debug("Participant {} added to event {}", conferenceId, eventId);
            }
        } catch (final Exception e) {
            log.error("Error adding participant to event: {} -> {}", conferenceId, eventId, e);
            throw new RuntimeException("Failed to add participant to event", e);
        }
    }

    // ПОЛУЧЕНИЕ ВСЕХ УЧАСТНИКОВ
    public Set<String> getParticipants(final String eventId) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            final String key = eventParticipantsKey(eventId);
            return redisTemplate.opsForSet().members(key).stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
        } catch (final Exception e) {
            log.error("Error getting participants for event: {}", eventId, e);
            return Set.of();
        }
    }

    // ПРОВЕРКА ЯВЛЯЕТСЯ ЛИ УЧАСТНИКОМ
    public boolean isParticipant(final String eventId, final String conferenceId) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            final String key = eventParticipantsKey(eventId);
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, conferenceId));
        } catch (final Exception e) {
            log.error("Error checking participant: {} in event: {}", conferenceId, eventId, e);
            return false;
        }
    }

    // УДАЛЕНИЕ УЧАСТНИКА
    public void removeParticipant(final String eventId, final String conferenceId) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            final String key = eventParticipantsKey(eventId);
            final Long removed = redisTemplate.opsForSet().remove(key, conferenceId);
            if (removed != null && removed > 0) {
                log.debug("Participant {} removed from event {}", conferenceId, eventId);
            }
        } catch (final Exception e) {
            log.error("Error removing participant from event: {} -> {}", conferenceId, eventId, e);
            throw new RuntimeException("Failed to remove participant from event", e);
        }
    }

    // ПОЛУЧЕНИЕ КОЛИЧЕСТВА УЧАСТНИКОВ
    public Long getParticipantCount(final String eventId) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            final String key = eventParticipantsKey(eventId);
            return redisTemplate.opsForSet().size(key);
        } catch (final Exception e) {
            log.error("Error getting participant count for event: {}", eventId, e);
            return 0L;
        }
    }


    public void deleteEvent(final String eventId) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            // Удаляем все связанные ключи
            final ArrayList<String> keys = new ArrayList<>(List.of(
                RedisKeys.eventKey(eventId),
                RedisKeys.eventParticipantsKey(eventId),
                RedisKeys.eventPendingIdeasKey(eventId),
                RedisKeys.eventAcceptedIdeasKey(eventId)
            ));

            redisTemplate.delete(keys);
            log.debug("Event completely deleted from Redis: {}", eventId);
        } catch (final Exception e) {
            log.error("Error deleting event from Redis: {}", eventId, e);
            throw new RuntimeException("Failed to delete event from Redis", e);
        }
    }

    // ПОЛУЧЕНИЕ СТАТУСА СОБЫТИЯ
    public Optional<EventStatus> getEventStatus(final String eventId) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            final Optional<Event> event = findEventById(eventId);
            return event.map(Event::getStatus);
        } catch (final Exception e) {
            log.error("Error getting event status: {}", eventId, e);
            return Optional.empty();
        }
    }

    // СОХРАНЕНИЕ ИМЕНИ КОНФЕРЕНЦИИ
    public void saveConferenceName(final String conferenceId, final String conferenceName, final String eventId) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            // Сохраняем имя конференции по её ID
            final String nameKey = RedisKeys.conferenceNameKey(conferenceId);
            redisTemplate.opsForValue().set(nameKey, conferenceName);

            // Сохраняем обратное соответствие: имя -> ID (для быстрого поиска)
            final String nameToIdKey = RedisKeys.eventConferenceNameKey(eventId, conferenceName);
            redisTemplate.opsForValue().set(nameToIdKey, conferenceId);

            log.debug("✅ Saved conference name: conferenceId={}, conferenceName={}, eventId={}",
                    conferenceId, conferenceName, eventId);
        } catch (final Exception e) {
            log.error("❌ Error saving conference name: conferenceId={}, conferenceName={}", 
                    conferenceId, conferenceName, e);
        }
    }

    // ПОЛУЧЕНИЕ КОНФЕРЕНЦИИ ПО ИМЕНИ В EVENT
    public Optional<String> findConferenceByName(final String eventId, final String conferenceName) {
        try {
            if (!isRedisConnected()) {
                throw new RuntimeException("Redis is not connected");
            }
            final String key = RedisKeys.eventConferenceNameKey(eventId, conferenceName);
            final Object result = redisTemplate.opsForValue().get(key);
            
            if (result != null) {
                final String conferenceId = result.toString();
                log.debug("✅ Found conference by name: eventId={}, conferenceName={}, conferenceId={}",
                        eventId, conferenceName, conferenceId);
                return Optional.of(conferenceId);
            }
            
            log.debug("⚠️ No conference found for: eventId={}, conferenceName={}", eventId, conferenceName);
            return Optional.empty();
        } catch (final Exception e) {
            log.error("❌ Error finding conference by name: eventId={}, conferenceName={}", 
                    eventId, conferenceName, e);
            return Optional.empty();
        }
    }
}
