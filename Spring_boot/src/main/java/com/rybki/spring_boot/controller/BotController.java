package com.rybki.spring_boot.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rybki.spring_boot.model.domain.api.bot.create.CreateBotRequest;
import com.rybki.spring_boot.model.domain.api.bot.create.CreateBotResponse;
import com.rybki.spring_boot.service.BotService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/bot")
@RequiredArgsConstructor
@Tag(name = "Bot API", description = "Operations with bot")
public class BotController {
    private final BotService botService;

    @PostMapping("create")
    @Operation(summary = "Create Bot", description = "Create a new bot for the conference")
    @ApiResponse(responseCode = "200", description = "Bot created successfully")
    @ApiResponse(responseCode = "400", description = "Bad Request - validation error")
    @ApiResponse(responseCode = "422", description = "Unprocessable Entity")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    public ResponseEntity<CreateBotResponse> createBot(final @RequestBody @Valid CreateBotRequest request) {
        final CreateBotResponse response = botService.handleCreateBotRequest(request);
        return ResponseEntity.ok(response);
    }
    
}
