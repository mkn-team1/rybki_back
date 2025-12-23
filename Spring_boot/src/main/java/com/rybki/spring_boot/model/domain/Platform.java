package com.rybki.spring_boot.model.domain;

public enum Platform {
    KONTUR_TALK("kontur_talk");

    private final String platformName;

    Platform(String platformName) {
        this.platformName = platformName;
    }

    public String getPlatformName() {
        return platformName;
    }
}
