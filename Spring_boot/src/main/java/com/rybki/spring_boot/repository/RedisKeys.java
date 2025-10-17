package com.rybki.spring_boot.repository;

import org.springframework.stereotype.Component;

@Component
public class RedisKeys {

    // Основные ключи
    public static String eventKey(final String eventId) {
        return "event:" + eventId;
    }

    public static String eventParticipantsKey(final String eventId) {
        return "event:" + eventId + ":participants";
    }

    public static String eventPendingIdeasKey(final String eventId) {
        return "event:" + eventId + ":pending_ideas";
    }

    public static String eventAcceptedIdeasKey(final String eventId) {
        return "event:" + eventId + ":accepted_ideas";
    }

    public static String ideaKey(final String ideaId) {
        return "idea:" + ideaId;
    }

    public static String ideaVotesKey(final String ideaId) {
        return "idea:" + ideaId + ":votes";
    }

    public static String clientSessionKey(final String clientId) {
        return "client:" + clientId + ":session";
    }
}