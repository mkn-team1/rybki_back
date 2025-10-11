package com.rybki.spring_boot.model.dto;

import java.util.List;

public record GigaChatResponseDto(
    List<Choice> choices,
    long created,
    String model,
    String object,
    Usage usage
) {
    public record Choice(
        Message message,
        int index,
        String finishReason
    ) {
    }

    public record Message(
        String role,
        String content
    ) {
    }

    public record Usage(
        int promptTokens,
        int completionTokens,
        int totalTokens
    ) {
    }
}