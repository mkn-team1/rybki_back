package com.rybki.spring_boot.model.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NnResponseDto(
    String status,
    List<NnIdea> ideas,
    Meta meta
) {
    public record NnIdea(String id, String title, String description, String category) {
    }

    public record Meta(
        @JsonProperty("total_ideas")
        int totalIdeas,

        @JsonProperty("source_text_length") 
        int sourceTextLength
    ) {
    }
}