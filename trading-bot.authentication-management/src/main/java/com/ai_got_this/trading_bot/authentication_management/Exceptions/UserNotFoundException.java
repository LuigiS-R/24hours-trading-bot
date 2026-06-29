package com.ai_got_this.trading_bot.authentication_management.Exceptions;

public class UserNotFoundException extends AuthenticationException {
    public UserNotFoundException(String username) {
        super("User " + username + " not found");
    }
}
