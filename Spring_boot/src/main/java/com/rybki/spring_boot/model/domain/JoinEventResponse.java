package com.rybki.spring_boot.model.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JoinEventResponse {
    private String conferenceId;
    private String conferenceName;
    private String eventName;
    private String eventId;
}
