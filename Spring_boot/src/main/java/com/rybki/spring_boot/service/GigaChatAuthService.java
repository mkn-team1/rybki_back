package com.rybki.spring_boot.service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.rybki.spring_boot.model.dto.GigaChatTokenDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class GigaChatAuthService {

    private final WebClient webClient = WebClient.builder().build();
    
    private final AtomicReference<String> accessToken = new AtomicReference<>();
    private final AtomicReference<Long> tokenExpiresAt = new AtomicReference<>();

    @Value("${gigachat.oauth.url}")
    private String oauthUrl;

    @Value("${gigachat.auth.key}")
    private String authorizationKey;

    @PostConstruct
    public void init() {
        refreshToken()
            .doOnSuccess(token -> log.info("✅ [GIGACHAT-AUTH] Initial GigaChat token obtained"))
            .doOnError(e -> log.error("❌ [GIGACHAT-AUTH] Failed to obtain initial token", e))
            .subscribe();
    }

    @Scheduled(fixedRate = 1800000) // 30 минут
    public void scheduledRefresh() {
        refreshToken()
            .doOnSuccess(token -> log.info("✅ [GIGACHAT-AUTH] Scheduled token refresh completed"))
            .doOnError(e -> log.error("❌ [GIGACHAT-AUTH] Scheduled token refresh failed", e))
            .subscribe();
    }

    public Mono<String> refreshToken() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("scope", "GIGACHAT_API_PERS");

        return webClient.post()
            .uri(oauthUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Basic " + authorizationKey)
            .header("RqUID", UUID.randomUUID().toString())
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .bodyToMono(GigaChatTokenDto.class)
            .doOnNext(tokenDto -> {
                accessToken.set(tokenDto.accessToken());
                tokenExpiresAt.set(tokenDto.expiresAt());
                log.info("✅ [GIGACHAT-AUTH] Access token refreshed, expires at: {}", tokenDto.expiresAt());
            })
            .map(GigaChatTokenDto::accessToken)
            .doOnError(e -> log.error("❌ [GIGACHAT-AUTH] Failed to refresh access token", e))
            .onErrorResume(e -> Mono.empty());
    }

    public Mono<String> getAccessToken() {
        String currentToken = accessToken.get();
        Long expiresAt = tokenExpiresAt.get();

        if (currentToken == null || expiresAt == null || System.currentTimeMillis() >= expiresAt) {
            log.info("Token expired or not exists, refreshing...");
            return refreshToken();
        }

        return Mono.just(currentToken);
    }
}
