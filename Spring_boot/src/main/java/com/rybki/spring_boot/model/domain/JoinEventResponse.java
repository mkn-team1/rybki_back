package com.rybki.spring_boot.model.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class JoinEventResponse {

    private String eventId;
    private String clientId;
}
