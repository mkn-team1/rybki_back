package com.rybki.spring_boot.model.domain.redis;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Participant {

    private String clientId;
    private String eventId;
    private Instant joinedAt;
    private ParticipantRole role; // CREATOR, MEMBER
}

