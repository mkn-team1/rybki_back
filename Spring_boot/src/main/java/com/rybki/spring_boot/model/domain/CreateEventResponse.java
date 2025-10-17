package com.rybki.spring_boot.model.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class CreateEventResponse {

    private String clientId;
    private String eventId;
    private boolean isCreator = true;
    private String joinToken;
}
