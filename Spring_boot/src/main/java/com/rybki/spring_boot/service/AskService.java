package com.rybki.spring_boot.service;

import org.springframework.stereotype.Service;

import com.rybki.spring_boot.client.QuestionAskerClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AskService {
 
    private final QuestionAskerClient questionAskerClient;
    private final ClientNotificationService clientNotificationService;

    public Mono<Void> processQuestion(final String question, final String conferenceId) {
        return questionAskerClient.askQuestion(question)
                .flatMap(responseContent -> {
                    log.info("✅ [ASK_SERVICE] Sending response back to conferenceId={}", conferenceId);
                    return clientNotificationService.sendQuestionAnswer(conferenceId, question, responseContent);
                });
    }
}
