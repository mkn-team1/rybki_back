package com.rybki.spring_boot.service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Локальный реестр соответствия clientID -> (conferenceId, eventId, conferenceName)
 * Используется для автоматического определения конференции по clientID
 * при обработке идей от STT
 */
@Slf4j
@Service
public class ClientRegistryService {

    public record ClientInfo(String conferenceId, String eventId, String conferenceName) {}

    // clientId -> ClientInfo
    private final ConcurrentMap<String, ClientInfo> registry = new ConcurrentHashMap<>();

    /**
     * Регистрирует clientID с его конференцией
     */
    public void registerClient(final String clientId, final String conferenceId, final String eventId,
            final String conferenceName) {
        registry.put(clientId, new ClientInfo(conferenceId, eventId, conferenceName));
        log.debug("📝 Registered clientId={} with conferenceId={}, eventId={}, conferenceName={}", 
                clientId, conferenceId, eventId, conferenceName);
    }

    /**
     * Получить информацию о конференции по clientID
     */
    public Optional<ClientInfo> getClientInfo(final String clientId) {
        ClientInfo info = registry.get(clientId);
        if (info != null) {
            log.debug("✅ Found clientInfo for clientId={}: conferenceId={}, eventId={}, conferenceName={}", 
                    clientId, info.conferenceId, info.eventId, info.conferenceName);
            return Optional.of(info);
        }
        log.warn("❌ No clientInfo found for clientId={}", clientId);
        return Optional.empty();
    }

    /**
     * Удалить регистрацию clientID
     */
    public void unregisterClient(final String clientId) {
        registry.remove(clientId);
        log.debug("🗑️ Unregistered clientId={}", clientId);
    }

    /**
     * Получить размер реестра (для мониторинга)
     */
    public int size() {
        return registry.size();
    }

    /**
     * Очистить весь реестр (для тестирования)
     */
    public void clear() {
        registry.clear();
        log.debug("🗑️ Cleared entire registry");
    }
}
