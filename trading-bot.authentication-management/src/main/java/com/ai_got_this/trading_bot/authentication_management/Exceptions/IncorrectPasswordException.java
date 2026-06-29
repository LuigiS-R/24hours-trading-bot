package com.ai_got_this.trading_bot.authentication_management.Exceptions;

public class IncorrectPasswordException extends AuthenticationException {
    public IncorrectPasswordException() {
        super("Incorrect password");
    }
}
