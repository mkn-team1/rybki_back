package com.rybki.spring_boot.model.domain;

import com.rybki.spring_boot.model.domain.redis.VoteType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteRequest {

    private String clientId;
    private VoteType vote;
}

