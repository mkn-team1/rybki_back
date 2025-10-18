package com.rybki.spring_boot.model.domain;

import lombok.Getter;
import lombok.Setter;

public class CreateEventRequest {

    @Getter
    @Setter
    private String clientId;
    @Getter
    @Setter
    private EventDetails eventDetails;
}
