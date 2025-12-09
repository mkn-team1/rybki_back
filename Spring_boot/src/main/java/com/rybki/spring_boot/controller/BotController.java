package com.rybki.spring_boot.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rybki.spring_boot.service.BotService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/bot")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Bot API", description = "Bot notifications and management")
public class BotController {

    private final BotService botService;

    /**
     * Уведомление от бота о успешном подключении к конференции.
     * Сохраняет информацию и отправляет уведомление всем клиентам на фронт.
     *
     * @param botId идентификатор бота
     * @return пустой ответ с кодом 200
     */
    @PostMapping("/{botId}/started")
    @Operation(summary = "Bot Started", description = "Notify backend that bot successfully connected to conference")
    @ApiResponse(responseCode = "200", description = "Bot started notification received")
    @ApiResponse(responseCode = "400", description = "Bad Request - botId not found or invalid")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    public Mono<ResponseEntity<Void>> botStarted(final @PathVariable String botId) {
        log.info("🚀 [BOT-CONTROLLER] Bot started notification received: botId={}", botId);

        // Получаем conferenceId и eventId по botId
        final String conferenceId = botService.getConferenceId(botId);
        final String eventId = botService.getEventId(botId);

        if (conferenceId == null || eventId == null) {
            log.warn("⚠️ [BOT-CONTROLLER] Bot not found in registry: botId={}", botId);
            return Mono.just(ResponseEntity.badRequest().build());
        }

        // Вызываем connectBot для отправки уведомления фронту
        return botService.connectBot(conferenceId, eventId, botId)
                .then(Mono.fromCallable(() -> {
                    log.info("✅ [BOT-CONTROLLER] Bot started successfully: botId={}, conferenceId={}, eventId={}",
                            botId, conferenceId, eventId);
                    return ResponseEntity.ok().<Void>build();
                }))
                .onErrorResume(e -> {
                    log.error("❌ [BOT-CONTROLLER] Error processing bot started notification: botId={}, error={}",
                            botId, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * Уведомление от бота об отключении от конференции.
     * Удаляет информацию о боте и отправляет уведомление всем клиентам на фронт.
     *
     * @param botId идентификатор бота
     * @return пустой ответ с кодом 200
     */
    @PostMapping("/{botId}/removed")
    @Operation(summary = "Bot Removed", description = "Notify backend that bot disconnected from conference")
    @ApiResponse(responseCode = "200", description = "Bot removed notification received")
    @ApiResponse(responseCode = "400", description = "Bad Request - botId not found or invalid")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    public Mono<ResponseEntity<Void>> botRemoved(final @PathVariable String botId) {
        log.info("🛑 [BOT-CONTROLLER] Bot removed notification received: botId={}", botId);

        // Получаем conferenceId и eventId по botId перед удалением
        final String conferenceId = botService.getConferenceId(botId);
        final String eventId = botService.getEventId(botId);

        if (conferenceId == null || eventId == null) {
            log.warn("⚠️ [BOT-CONTROLLER] Bot not found in registry: botId={}", botId);
            return Mono.just(ResponseEntity.badRequest().build());
        }

        // Вызываем disconnectBot для отправки уведомления фронту
        return botService.disconnectBot(conferenceId, eventId, botId)
                .then(Mono.fromCallable(() -> {
                    log.info("✅ [BOT-CONTROLLER] Bot removed successfully: botId={}, conferenceId={}, eventId={}",
                            botId, conferenceId, eventId);
                    return ResponseEntity.ok().<Void>build();
                }))
                .onErrorResume(e -> {
                    log.error("❌ [BOT-CONTROLLER] Error processing bot removed notification: botId={}, error={}",
                            botId, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }
}
