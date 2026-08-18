package com.ai_got_this.account_service.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai_got_this.account_service.service.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

    @Test
    void apiExceptionProducesItsStatusAndATraceableErrorBody() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<ApiExceptionHandler.Error> response = handler.api(new ApiException(
                HttpStatus.CONFLICT,
                "ALPACA_REAUTH_REQUIRED",
                "Reconnect the account."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("ALPACA_REAUTH_REQUIRED");
        assertThat(response.getBody().correlationId()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void unexpectedExceptionDoesNotExposeItsMessage() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<ApiExceptionHandler.Error> response = handler.other(new IllegalStateException("secret detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Unexpected service error.");
    }
}
