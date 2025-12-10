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
public class Idea {

    private String ideaId;
    private String eventId;
    private String clientId; // кто предложил
    private String title;
    private String description;
    private IdeaStatus status; // PENDING, ACCEPTED, REJECTED
    private Instant createdAt;
    private String sourceText; // оригинальный текст от STT
}
