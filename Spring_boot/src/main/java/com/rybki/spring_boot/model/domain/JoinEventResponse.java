package com.rybki.spring_boot.model.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class JoinEventResponse {

    private String eventId;
    private String clientId;
}
