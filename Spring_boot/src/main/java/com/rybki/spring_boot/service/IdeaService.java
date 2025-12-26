package com.rybki.spring_boot.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.rybki.spring_boot.client.IdeaExtractorClient;
import com.rybki.spring_boot.model.domain.Idea;
import com.rybki.spring_boot.model.domain.redis.IdeaStatus;
import com.rybki.spring_boot.repository.RedisIdeaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdeaService {

    private final IdeaExtractorClient ideaExtractorClient;
    private final ClientNotificationService clientNotificationService;
    private final RedisIdeaRepository ideaRepository;
    private final SessionService sessionService;

    public Mono<Void> processText(String conferenceId, String conferenceName, String eventId, String text) {
        log.info("💡 [IDEA-SERVICE] Starting idea extraction: conferenceId={}, eventId={}, textLength={} chars",
                conferenceId, eventId, text.length());

        return ideaExtractorClient.extractIdeas(text)
                .flatMap(ideas -> processIdeas(conferenceId, conferenceName, eventId, ideas))
                .doOnError(e -> log.error("❌ [IDEA-SERVICE] Failed to process ideas for conferenceId={}, eventId={}",
                        conferenceId, eventId, e))
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> processIdeas(String conferenceId, String conferenceName, String eventId, List<Idea> ideas) {
        if (ideas == null || ideas.isEmpty()) {
            log.debug("⚠️ [IDEA-SERVICE] No ideas found for conferenceId={}, eventId={}", conferenceId, eventId);
            return Mono.empty();
        }

        log.debug("💡 [IDEA-SERVICE] Found {} ideas", ideas.size());

        return Flux.fromIterable(ideas)
                .flatMap(idea -> createAndSendIdea(conferenceId, conferenceName, eventId, idea))
                .then();
    }

    private Mono<Void> createAndSendIdea(String conferenceId, String conferenceName, String eventId, Idea idea) {
        final String safeName = conferenceName != null && !conferenceName.isEmpty() ? conferenceName : "Anonymous";
        final com.rybki.spring_boot.model.domain.redis.Idea redisIdea = com.rybki.spring_boot.model.domain.redis.Idea
                .builder()
                .ideaId(UUID.randomUUID().toString())
                .eventId(eventId)
                .conferenceId(conferenceId)
                .conferenceName(safeName)
                .title(idea.title())
                .description(idea.description())
                .status(IdeaStatus.LOCAL)
                .createdAt(OffsetDateTime.now(ZoneId.systemDefault()).toString())
                .author(safeName)
                .likes(0)
                .dislikes(0)
                .myReaction(null)
                .promotedToGlobalAt(null)
                .promotedToGoldenAt(null)
                .sourceText("")
                .build();

        return Mono.fromRunnable(() -> ideaRepository.saveIdea(redisIdea))
                .then(clientNotificationService.broadcastIdeaToConference(conferenceId, eventId, redisIdea))
                .doOnSuccess(v -> log.info("✅ [IDEA-SERVICE] Idea created and broadcast to conference: ideaId={}, title={}, conferenceId={}", 
                        redisIdea.getIdeaId(), redisIdea.getTitle(), conferenceId));
    }

    public Mono<Void> createIdeaFromFront(final String conferenceId, final String conferenceName,
            final String eventId, final String title, final String description) {
        final String safeName = conferenceName != null && !conferenceName.isEmpty() ? conferenceName : "Anonymous";
        log.info("🔍 [IDEA-SERVICE] createIdeaFromFront called: conferenceId={}, conferenceName={}, safeName={}, eventId={}, title={}",
                conferenceId, conferenceName, safeName, eventId, title);
        final com.rybki.spring_boot.model.domain.redis.Idea idea = com.rybki.spring_boot.model.domain.redis.Idea
                .builder()
                .ideaId(UUID.randomUUID().toString())
                .eventId(eventId)
                .conferenceId(conferenceId)
                .conferenceName(safeName)
                .title(title)
                .description(description)
                .status(IdeaStatus.LOCAL)
                .createdAt(OffsetDateTime.now(ZoneId.systemDefault()).toString())
                .author(safeName)
                .likes(0)
                .dislikes(0)
                .myReaction(null)
                .sourceText("")
                .build();

        return Mono.fromRunnable(() -> ideaRepository.saveIdea(idea))
                .then(clientNotificationService.broadcastIdeaToConference(conferenceId, eventId, idea))
                .doOnSuccess(v -> log.info("✅ [IDEA-SERVICE] Idea created from front and broadcast to conference: ideaId={}, conferenceId={}, conferenceName={}", 
                        idea.getIdeaId(), conferenceId, idea.getConferenceName()));
    }

    public Mono<Void> deleteIdea(String conferenceId, String eventId, String ideaId) {
        log.info("✅ [IDEA-SERVICE] Idea deletion marked for: ideaId={}", ideaId);
        return clientNotificationService.broadcastIdeaDeleted(conferenceId, eventId, ideaId);
    }

    public Mono<Void> reactToIdea(String conferenceId, String eventId, String ideaId, String reaction, String clientId) {
        return Mono.fromCallable(() -> ideaRepository.findIdeaById(ideaId))
                .flatMap(ideaOpt -> {
                    if (ideaOpt.isEmpty()) {
                        return Mono.empty();
                    }
                    final com.rybki.spring_boot.model.domain.redis.Idea r = ideaOpt.get();
                    if ("accept".equals(reaction)) {
                        return handleAcceptReaction(conferenceId, eventId, ideaId, r);
                    } else {
                        return handleLikeDislikeReaction(conferenceId, eventId, ideaId, reaction, clientId, r);
                    }
                });
    }

    private Mono<Void> handleAcceptReaction(String conferenceId, String eventId, String ideaId,
            com.rybki.spring_boot.model.domain.redis.Idea idea) {
        idea.setStatus(IdeaStatus.GOLDEN);
        return Mono.fromRunnable(() -> ideaRepository.saveIdea(idea))
                .then(clientNotificationService.broadcastIdea(conferenceId, eventId, idea))
                .then(clientNotificationService.broadcastIdeaStatusChanged(eventId, conferenceId, ideaId, "golden"))
                .doOnSuccess(v -> log.info("✅ [IDEA-SERVICE] Idea accepted and broadcast: ideaId={}", ideaId));
    }

    private Mono<Void> handleLikeDislikeReaction(String conferenceId, String eventId, String ideaId,
            String reaction, String clientId, com.rybki.spring_boot.model.domain.redis.Idea idea) {
        // Определяем, голосует ли клиент за свою LOCAL идею или за чужую GLOBAL/GOLDEN
        final boolean isOwnIdea = idea.getConferenceId().equals(conferenceId);
        final boolean isGlobalOrGoldenIdea = idea.getStatus() == IdeaStatus.GLOBAL || idea.getStatus() == IdeaStatus.GOLDEN;
        
        if (!isOwnIdea && !isGlobalOrGoldenIdea) {
            log.warn("⚠️ [IDEA-SERVICE] Cannot vote for LOCAL idea from another conference: ideaId={}, clientConferenceId={}, ideaConferenceId={}",
                    ideaId, conferenceId, idea.getConferenceId());
            return Mono.empty();
        }
        
        if ("like".equals(reaction)) {
            handleLikeVote(idea, clientId);
        } else if ("dislike".equals(reaction)) {
            handleDislikeVote(idea, clientId);
        }
        
        // Получаем актуальные значения из sets
        final int likes = idea.getLikesClientsSet().size();
        final int dislikes = idea.getDislikesClientsSet().size();
        
        log.info("✅ [IDEA-SERVICE] Reaction added: ideaId={}, reaction={}, clientId={}, likes={}, dislikes={}, ideaStatus={}",
                ideaId, reaction, clientId, likes, dislikes, idea.getStatus());
        
        // Сохраняем идею в Redis после обновления голоса
        Mono<Void> saveMono = Mono.fromRunnable(() -> ideaRepository.saveIdea(idea));
        
        // Для GLOBAL и GOLDEN идей рассылаем реакцию всему Event, для LOCAL - только конференции
        final Mono<Void> broadcastReaction;
        if (isGlobalOrGoldenIdea) {
            // Рассылаем всему Event
            broadcastReaction = clientNotificationService.broadcastIdeaReactionToEvent(eventId, ideaId, likes, dislikes);
        } else {
            // Рассылаем только своей конференции
            broadcastReaction = clientNotificationService.broadcastIdeaReaction(conferenceId, ideaId, likes, dislikes);
        }
        
        // Проверяем условие promotion: likes > 50% от количества людей в конференции (только для LOCAL идей)
        if (!isGlobalOrGoldenIdea) {
            final int participantsCount = sessionService.getParticipantsCountForConference(idea.getConferenceId());
            final boolean shouldPromoteToGlobal = likes > participantsCount / 2.0 
                    && idea.getPromotedToGlobalAt() == null;
            
            if (shouldPromoteToGlobal) {
                idea.setPromotedToGlobalAt(OffsetDateTime.now(ZoneId.systemDefault()).toString());
                idea.setStatus(IdeaStatus.GLOBAL);
                log.info("🚀 [IDEA-SERVICE] Idea promoted to GLOBAL: ideaId={}, likes={}, participants={}", 
                        ideaId, likes, participantsCount);
                
                // Сохраняем идею после промоции и отправляем всем конференциям в Event (кроме исходной)
                return saveMono
                        .then(broadcastReaction)
                        .then(Mono.fromRunnable(() -> ideaRepository.saveIdea(idea)))
                        .then(clientNotificationService.broadcastIdea(idea.getConferenceId(), eventId, idea))
                        .then(clientNotificationService.broadcastIdeaStatusChanged(eventId, idea.getConferenceId(), ideaId, "global"));
            }
        }
        
        return saveMono.then(broadcastReaction);
    }

    private void handleLikeVote(com.rybki.spring_boot.model.domain.redis.Idea idea, String clientId) {
        if (!idea.getLikesClientsSet().contains(clientId)) {
            idea.getLikesClientsSet().add(clientId);
            // Если был dislike, удаляем его
            idea.getDislikesClientsSet().remove(clientId);
        }
    }

    private void handleDislikeVote(com.rybki.spring_boot.model.domain.redis.Idea idea, String clientId) {
        if (!idea.getDislikesClientsSet().contains(clientId)) {
            idea.getDislikesClientsSet().add(clientId);
            // Если был like, удаляем его
            idea.getLikesClientsSet().remove(clientId);
        }
    }

    public Mono<List<com.rybki.spring_boot.model.domain.redis.Idea>> getIdeasForEvent(String eventId) {
        return Mono.fromCallable(() -> {
            final var pendingIds = ideaRepository.getPendingIdeas(eventId);
            return pendingIds.stream()
                    .map(ideaRepository::findIdeaById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .toList();
        });
    }

    /**
     * Отправить все существующие идеи новому подключившемуся клиенту:
     * - LOCAL идеи своей конференции
     * - Все GLOBAL и GOLDEN идеи из Event
     */
    public Mono<Void> sendExistingIdeasToClient(String conferenceId, String eventId) {
        return Mono.fromCallable(() -> {
            final Set<String> localIdeaIds = ideaRepository.getLocalIdeasForConference(eventId, conferenceId);
            final Set<String> globalAndGoldenIds = ideaRepository.getGlobalAndGoldenIdeas(eventId);
            
            // Объединяем LOCAL идеи конференции с GLOBAL/GOLDEN идеями Event
            final Set<String> allIdeaIds = new java.util.HashSet<>(localIdeaIds);
            allIdeaIds.addAll(globalAndGoldenIds);
            
            if (allIdeaIds.isEmpty()) {
                log.debug("📭 [IDEA-SERVICE] No existing ideas for conferenceId={}", conferenceId);
                return List.<com.rybki.spring_boot.model.domain.redis.Idea>of();
            }
            log.info("📬 [IDEA-SERVICE] Sending {} existing ideas to conferenceId={} (LOCAL={}, GLOBAL/GOLDEN={})", 
                    allIdeaIds.size(), conferenceId, localIdeaIds.size(), globalAndGoldenIds.size());
            return allIdeaIds.stream()
                    .map(ideaRepository::findIdeaById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .toList();
        })
        .flatMapMany(reactor.core.publisher.Flux::fromIterable)
        .flatMap(idea -> clientNotificationService.broadcastIdeaToConference(conferenceId, eventId, idea))
        .then();
    }
}
