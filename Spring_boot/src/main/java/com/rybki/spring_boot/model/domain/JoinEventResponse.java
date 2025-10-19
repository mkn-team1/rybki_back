package com.rybki.spring_boot.model.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JoinEventResponse {

    private String eventId;
    private String clientId;
}
