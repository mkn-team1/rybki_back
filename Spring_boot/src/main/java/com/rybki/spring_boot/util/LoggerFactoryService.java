package com.rybki.spring_boot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Фабрика для создания LoggerService для конкретного класса.
 */
public class LoggerFactoryService {

    public static LoggerService getLogger(final Class<?> clazz) {
        final Logger logger = LoggerFactory.getLogger(clazz);
        return new LoggerService(logger);
    }
}
