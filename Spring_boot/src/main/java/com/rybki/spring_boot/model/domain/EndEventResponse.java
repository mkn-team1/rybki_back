package com.rybki.spring_boot.model.domain;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

public class EndEventResponse {

    @Nullable
    @Schema(
        description = "Метаданные события",
        example = "{\"priority\": \"high\", \"category\": \"meeting\"}",
        nullable = true
    )
    private Map<String, Object> metadata;
}
