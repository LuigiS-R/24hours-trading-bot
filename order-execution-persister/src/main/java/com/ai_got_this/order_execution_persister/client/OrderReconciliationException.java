package com.ai_got_this.order_execution_persister.client;

public class OrderReconciliationException extends RuntimeException {
    private final String code;

    public OrderReconciliationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
