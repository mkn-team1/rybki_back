package com.rybki.spring_boot.model.domain;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
@Getter
@Setter
public class CreateEventRequest {

    @Nullable
    @Schema(
        description = "ID клиента",
        example = "client_12345",  // Пример реального значения
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String clientId;

    @Nullable
    @Schema(
        description = "Метаданные события",
        example = "{\"priority\": \"high\", \"category\": \"meeting\"}",
        nullable = true
    )
    private Map<String, Object> metadata;
}

