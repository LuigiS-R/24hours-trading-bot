package com.ai_got_this.account_service.web;

import com.ai_got_this.account_service.service.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    record Error(String code, String message, UUID correlationId, Instant timestamp) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Error> api(ApiException e) {
        return ResponseEntity
                .status(e.status)
                .body(new Error(e.code, e.getMessage(), UUID.randomUUID(), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> other(Exception e) {
        return ResponseEntity
                .status(500)
                .body(new Error("INTERNAL_ERROR", "Unexpected service error.", UUID.randomUUID(), Instant.now()));
    }
}
