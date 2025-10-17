package com.rybki.spring_boot.model.domain.redis;

import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisEvent {

    private String eventId;
    private String creatorClientId;
    private EventStatus status; // ACTIVE, ENDED
    private Instant createdAt;
    private Instant endedAt;
    private Map<String, Object> metadata;
}