package com.rybki.spring_boot.controller;

import com.rybki.spring_boot.model.domain.CustomErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /* for future exceptions
        @ExceptionHandler(EventNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleEventNotFound(EventNotFoundException ex, WebRequest request) {
            ErrorResponse error = ErrorResponse.builder()
                .errorCode("EVENT_NOT_FOUND")
                .message(ex.getMessage())
                .path(request.getDescription(false))
                .build();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        @ExceptionHandler(InvalidEventDataException.class)
        public ResponseEntity<ErrorResponse> handleInvalidEventData(InvalidEventDataException ex, WebRequest request) {
            ErrorResponse error = ErrorResponse.builder()
                .errorCode("INVALID_EVENT_DATA")
                .message(ex.getMessage())
                .path(request.getDescription(false))
                .build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomErrorResponse> handleGenericException(final Exception ex, final WebRequest request) {
        final CustomErrorResponse error = CustomErrorResponse.builder()
            .errorCode("INTERNAL_SERVER_ERROR")
            .message("Произошла внутренняя ошибка")
            .path(request.getDescription(false))
            .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
