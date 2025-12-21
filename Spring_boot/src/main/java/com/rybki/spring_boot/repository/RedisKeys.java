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

    public static String eventRejectedIdeasKey(final String eventId) {
        return "event:" + eventId + ":rejected_ideas";
    }

    public static String ideaKey(final String ideaId) {
        return "idea:" + ideaId;
    }

    public static String ideaVotesKey(final String ideaId) {
        return "idea:" + ideaId + ":votes";
    }

    public static String ideaLikesKey(final String ideaId) {
        return "idea:" + ideaId + ":likes";
    }

    public static String ideaDislikesKey(final String ideaId) {
        return "idea:" + ideaId + ":dislikes";
    }

    public static String clientSessionKey(final String clientId) {
        return "client:" + clientId + ":session";
    }

    // conferenceName -> conferenceId (для быстрого поиска конференции по имени в event)
    public static String eventConferenceNameKey(final String eventId, final String conferenceName) {
        return "event:" + eventId + ":conference_name:" + conferenceName;
    }

    // conferenceId -> conferenceName (для хранения имён конференций)
    public static String conferenceNameKey(final String conferenceId) {
        return "conference:" + conferenceId + ":name";
    }
}