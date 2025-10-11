package com.rybki.spring_boot.util;

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

public record LoggerService(Logger logger) {

    public void info(final String msg, final Object... args) {
        logger.info(msg, args);
    }

    public void debug(final String msg, final Object... args) {
        logger.debug(msg, args);
    }

    public void warn(final String msg, final Object... args) {
        logger.warn(msg, args);
    }

    public void error(final String msg, final Object... args) {
        logger.error(msg, args);
    }

    public void error(final String msg, final Throwable ex) {
        logger.error(msg, ex);
    }
}
