package com.ai_got_this.news_ingestion_service.exception;

public class ErrorAlpacaMessageException extends RuntimeException {
    public ErrorAlpacaMessageException(String message) {
        super(message);
    }
}