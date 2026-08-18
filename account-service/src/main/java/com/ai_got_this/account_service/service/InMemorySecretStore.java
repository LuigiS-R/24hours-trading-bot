package com.ai_got_this.account_service.service;

import java.util.*;
import org.springframework.stereotype.Component;

/**
  * Replace this adapter with a secret-manager-backed implementation before production deployment.
  */
@Component
public class InMemorySecretStore implements SecretStore {
    private final Map<String, String> values = new HashMap<>();

    public String store(String token) {
        String id = "secret://alpaca/" + UUID.randomUUID();
        values.put(id, token);
        return id;
    }

    public String read(String ref) {
        return values.get(ref);
    }

    public void revoke(String ref) {
        values.remove(ref);
    }
}
