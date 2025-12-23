package com.rybki.spring_boot.model.domain.api.event.summarize;

import lombok.Data;

@Data
public class SummarizeEventRequest {
    
    /**
     * Режим извлечения идей для summary:
     * "all" - все идеи (pending + accepted),
     * "accepted_only" - только принятые идеи
     */
    private String mode = "all";

    /**
     * Стиль summary:
     * "detailed" - подробное описание,
     * "short" - краткий вариант,
     * "bullet_points" - с пунктами
     */
    private String style = "detailed";
}

