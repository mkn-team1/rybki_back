package com.rybki.spring_boot.model.domain;

import lombok.Getter;
import lombok.Setter;

public class EndEventRequest {

    @Getter
    @Setter
    private String eventId;
    @Getter
    @Setter
    private EventDetails eventDetails;
}