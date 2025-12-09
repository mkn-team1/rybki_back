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
    private String conferenceId; // кто предложил
    private String conferenceName; // имя команды
    private String title;
    private String description;
    private IdeaStatus status; // PENDING, ACCEPTED, REJECTED
    private Instant createdAt;
    private String author; // имя автора
    private Integer likes;
    private Integer dislikes;
    private String myReaction; // null, "like", "dislike"
    private Instant promotedToGlobalAt;
    private Instant promotedToGoldenAt;
    private String sourceText; // оригинальный текст от STT
}
