package com.rybki.spring_boot.model.dto;

import java.util.List;

public record NnResponseDto(
    String status,
    List<NnIdea> ideas,
    Meta meta
) {
    public record NnIdea(String id, String title, String description, String category) {
    }

    public record Meta(
        int totalIdeas,
        int sourceTextLength
    ) {
    }
}