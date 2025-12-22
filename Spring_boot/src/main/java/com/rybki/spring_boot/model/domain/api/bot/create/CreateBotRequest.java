package com.rybki.spring_boot.model.domain.api.bot.create;

import lombok.Data;

@Data
public class CreateBotRequest {
    private String meetingUrl;
    private String conferenceId;
}
