package com.rybki.spring_boot.llm.gigachat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GigaChatTokenDto(
        @JsonProperty("access_token") String accessToken,

        @JsonProperty("expires_at") Long expiresAt) {
}
