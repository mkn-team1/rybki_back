package com.rybki.spring_boot.model.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateEventResponse {

    private String clientId;
    private String eventId;
    private String joinToken;
}
