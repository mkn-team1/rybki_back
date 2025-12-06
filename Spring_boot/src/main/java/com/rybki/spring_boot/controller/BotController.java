package com.rybki.spring_boot.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rybki.spring_boot.model.domain.api.bot.create.CreateBotRequest;
import com.rybki.spring_boot.model.domain.api.bot.create.CreateBotResponse;
import com.rybki.spring_boot.model.domain.api.bot.removed.RemovedBotRequest;
import com.rybki.spring_boot.model.domain.api.bot.removed.RemovedBotResponse;
import com.rybki.spring_boot.model.domain.api.bot.started.StartedBotRequest;
import com.rybki.spring_boot.model.domain.api.bot.started.StartedBotResponse;
import com.rybki.spring_boot.service.BotService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;


@RestController
@RequestMapping("/bot")
@RequiredArgsConstructor
@Tag(name = "Bot API", description = "Operations with bot")
public class BotController {

    private final BotService botService;
    
    // Создать бота для конференции
    @PostMapping("/create")
    @Operation(summary = "Create Bot", description = "Creates new bot for conference")
    @ApiResponse(responseCode = "200", description = "Bot Created")
    @ApiResponse(responseCode = "400", description = "Bad Request - validation error")
    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - wrong url format")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    public ResponseEntity<CreateBotResponse> createBot(
        final @RequestBody @Valid CreateBotRequest createBotRequest) {

        CreateBotResponse response = botService.createBot(createBotRequest);
        return ResponseEntity.ok(response);
    }

    // Уведомление о старте бота
    @PostMapping("/{botId}/started")
    @Operation(summary = "Bot Started", description = "Notify that bot has started")
    @ApiResponse(responseCode = "200", description = "Bot Started Handled")
    @ApiResponse(responseCode = "400", description = "Bad Request - validation error")
    @ApiResponse(responseCode = "404", description = "Bot not found")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    public ResponseEntity<StartedBotResponse> handleBotStarted(
        final @PathVariable String botId,
        final @RequestBody @Valid StartedBotRequest startedBotRequest) {

        StartedBotResponse response = botService.handleBotStarted(botId, startedBotRequest);
        return ResponseEntity.ok(response);
    }
    
    // Уведомление об удалении бота
    @PostMapping("/{botId}/removed")
    @Operation(summary = "Bot Removed", description = "Notify that bot has been removed")
    @ApiResponse(responseCode = "200", description = "Bot Removed Handled")
    @ApiResponse(responseCode = "400", description = "Bad Request - validation error")
    @ApiResponse(responseCode = "404", description = "Bot not found")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    public ResponseEntity<RemovedBotResponse> handleBotRemoved(
        final @PathVariable String botId,
        final @RequestBody @Valid RemovedBotRequest removedBotRequest) {

        RemovedBotResponse response = botService.handleBotRemoved(botId, removedBotRequest);
        return ResponseEntity.ok(response);
    }
}
