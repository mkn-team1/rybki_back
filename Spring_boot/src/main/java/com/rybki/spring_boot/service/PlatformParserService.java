package com.rybki.spring_boot.service;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.rybki.spring_boot.model.domain.Platform;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PlatformParserService {

    private static final Pattern KONTUR_TALK_PATTERN = Pattern.compile("^(https?://)?[a-zA-Z0-9-]+\\.ktalk\\.ru/[a-zA-Z0-9]+.*$");

    public Platform parsePlatform(final String meetingUrl) {
        if (meetingUrl == null || meetingUrl.isBlank()) {
            return null;
        }

        if (KONTUR_TALK_PATTERN.matcher(meetingUrl).matches()) {
            return Platform.KONTUR_TALK;
        }

        return null;
    }

    public String ensureProtocol(final String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }
}
