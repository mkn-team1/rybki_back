package com.rybki.spring_boot.service;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.rybki.spring_boot.model.dto.GigaChatTokenDto;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class GigaChatAuthService {

    private final WebClient webClient = WebClient.builder().build();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Value("${gigachat.oauth.url}")
    private String oauthUrl;

    @Value("${gigachat.auth.key}")
    private String authorizationKey;

    private String accessToken;
    private Long tokenExpiresAt;

    @PostConstruct
    public void init() {
        refreshToken();
    }

    @Scheduled(fixedRate = 1800000) // 30 минут = 1800000 миллисекунд
    public void refreshToken() {
        
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("scope", "GIGACHAT_API_PERS");

            GigaChatTokenDto tokenDto = webClient.post()
                .uri(oauthUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Basic " + authorizationKey)
                .header("RqUID", UUID.randomUUID().toString())
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(GigaChatTokenDto.class)
                .block();

            if (tokenDto != null) {
                lock.writeLock().lock();
                try {
                    this.accessToken = tokenDto.accessToken();
                    this.tokenExpiresAt = tokenDto.expiresAt();
                } finally {
                    lock.writeLock().unlock();
                }
            } else {
                
            }

        } catch (Exception e) {
            
        }
    }

    public String getAccessToken() {
        lock.readLock().lock();
        try {
            if (accessToken == null) {
                lock.readLock().unlock();
                refreshToken();
                lock.readLock().lock();
            }
            
            if (tokenExpiresAt != null && System.currentTimeMillis() >= tokenExpiresAt) {
                lock.readLock().unlock();
                refreshToken();
                lock.readLock().lock();
            }
            
            return accessToken;
        } finally {
            lock.readLock().unlock();
        }
    }

}