package com.rybki.spring_boot.model.domain.api.event.summarize;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SummarizeEventResponse {
    private String summaryText;
}
