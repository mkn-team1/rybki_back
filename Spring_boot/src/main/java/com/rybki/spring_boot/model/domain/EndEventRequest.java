package com.rybki.spring_boot.model.domain;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class EndEventRequest {

    private String clientId;
}
