package com.ai_got_this.account_service.service;

public interface SecretStore {
    String store(String token);

    String read(String reference);

    void revoke(String reference);
}
