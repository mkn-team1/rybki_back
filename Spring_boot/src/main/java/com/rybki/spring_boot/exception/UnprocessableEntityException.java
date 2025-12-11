package com.rybki.spring_boot.exception;

public class UnprocessableEntityException extends RuntimeException {
    public UnprocessableEntityException(final String message) {
        super(message);
    }
}
