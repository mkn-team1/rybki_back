package com.rybki.spring_boot.model.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CreateEventResponse {

    private String clientId;
    private String eventId;
    private boolean isCreator = true;
    private String joinToken;
}
