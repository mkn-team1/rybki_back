package com.rybki.spring_boot.model.domain.api.event.create;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateEventResponse {
    private String clientId;
    private String conferenceId;
    private String eventName;
    private String eventId;
}
