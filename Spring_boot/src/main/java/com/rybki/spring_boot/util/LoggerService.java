package com.rybki.spring_boot.service;

import org.slf4j.Logger;

/*
 *
 * Использование:
 * private static final LoggerService log = LoggerFactoryService.getLogger(IdeaService.class);
 * (для класса IdeaService, например)
 *
 * Далее в методах класса:
 * log.info("Processing text for clientId={}, eventId={}", clientId, eventId);
 * log.debug("Text content: {}", text);
 * log.error("Failed to process text for clientId={}, eventId={}", e);
 * и т.д.
 *
 */

public class LoggerService {

    private final Logger logger;

    public LoggerService(Logger logger) {
        this.logger = logger;
    }

    public void info(String msg, Object... args) {
        logger.info(msg, args);
    }

    public void debug(String msg, Object... args) {
        logger.debug(msg, args);
    }

    public void warn(String msg, Object... args) {
        logger.warn(msg, args);
    }

    public void error(String msg, Object... args) {
        logger.error(msg, args);
    }

    public void error(String msg, Throwable ex) {
        logger.error(msg, ex);
    }
}
