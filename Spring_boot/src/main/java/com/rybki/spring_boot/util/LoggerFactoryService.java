package com.yourorg.sttbackend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Фабрика для создания LoggerService для конкретного класса.
 */
public class LoggerFactoryService {

    public static LoggerService getLogger(Class<?> clazz) {
        Logger logger = LoggerFactory.getLogger(clazz);
        return new LoggerService(logger);
    }
}
