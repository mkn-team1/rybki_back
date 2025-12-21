package com.rybki.spring_boot.model.domain.redis;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
    private String createdAt;
    private String author; // имя автора
    private Integer likes;
    private Integer dislikes;
    private String myReaction; // null, "like", "dislike"
    private String promotedToGlobalAt;
    private String promotedToGoldenAt;
    private String sourceText; // оригинальный текст от STT

    @JsonIgnore
    @Builder.Default
    private Set<String> likesClientsSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
    @JsonIgnore
    @Builder.Default
    private Set<String> dislikesClientsSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Подготавливает идею к отправке клиенту: заполняет likes и dislikes из sets
     * Используется только при сериализации для WebSocket сообщений
     */
    @JsonIgnore
    public Idea prepareForSerialization() {
        this.likes = this.likesClientsSet.size();
        this.dislikes = this.dislikesClientsSet.size();
        return this;
    }
}
