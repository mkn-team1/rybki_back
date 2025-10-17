package com.rybki.spring_boot.model.domain.redis;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vote {

    private String voteId;
    private String ideaId;
    private String clientId;
    private VoteType vote; // ACCEPT, REJECT
    private Instant votedAt;
}

