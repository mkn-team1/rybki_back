package com.rybki.spring_boot.model.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcceptIdeaRequest {

    private String clientId;
}
