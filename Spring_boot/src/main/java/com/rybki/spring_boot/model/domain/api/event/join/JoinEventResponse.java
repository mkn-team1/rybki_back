package com.rybki.spring_boot.model.domain.api.event.join;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JoinEventResponse {
    private String clientId;
    private String conferenceId;
    private String conferenceName;
    private String eventName;
    private String eventId;
}
