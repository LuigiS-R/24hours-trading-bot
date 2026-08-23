package com.ai_got_this.account_service.web;

import com.ai_got_this.account_service.service.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    record Error(
            String code,
            String message,
            UUID correlationId,
            Instant timestamp) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Error> api(ApiException e) {

        UUID correlationId = UUID.randomUUID();

        log.warn(
                "API error [{}] correlationId={}: {}",
                e.code,
                correlationId,
                e.getMessage());

        return ResponseEntity
                .status(e.status)
                .body(new Error(
                        e.code,
                        e.getMessage(),
                        correlationId,
                        Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> other(Exception e) {

        UUID correlationId = UUID.randomUUID();

        log.error(
                "Unexpected service error correlationId={}",
                correlationId,
                e);

        return ResponseEntity
                .status(500)
                .body(new Error(
                        "INTERNAL_ERROR",
                        "Unexpected service error.",
                        correlationId,
                        Instant.now()));
    }
}