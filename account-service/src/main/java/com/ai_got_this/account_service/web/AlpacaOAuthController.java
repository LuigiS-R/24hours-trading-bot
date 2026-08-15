package com.ai_got_this.account_service.web;

import com.ai_got_this.account_service.domain.*;
import com.ai_got_this.account_service.repository.*;
import com.ai_got_this.account_service.service.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alpaca")
public class AlpacaOAuthController {
    final AlpacaOAuthService alpacaOAuthService;
    final CurrentUserService currentUserService;
    final AlpacaConnectionRepository alpacaConnectionRepository;
    final AccountReconciliationService accountReconciliationService;

    public AlpacaOAuthController(
            AlpacaOAuthService o,
            CurrentUserService c,
            AlpacaConnectionRepository r,
            AccountReconciliationService a) {
        alpacaOAuthService = o;
        currentUserService = c;
        alpacaConnectionRepository = r;
        accountReconciliationService = a;
    }

    @PostMapping("/connections/authorize")
    public AlpacaOAuthService.Authorization beginAuthorization() {
        return alpacaOAuthService.createAuthorizationUrl();
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<Void> handleCallback(@RequestParam String code, @RequestParam String state) {
        UUID id = alpacaOAuthService.consumeCallback(code, state);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/settings/connections/success?connectionId=" + id))
                .build();
    }

    @GetMapping("/connections")
    public List<AlpacaConnection> listConnections() {
        return alpacaConnectionRepository.findByUserId(currentUserService.getOrProvisionCurrentUser().getUserId());
    }

    @GetMapping("/connections/{id}")
    public AlpacaConnection getConnection(@PathVariable UUID id) {
        return currentUserService.requireOwnedConnection(id);
    }

    @PostMapping("/connections/{id}/reconcile")
    public Object reconcile(@PathVariable UUID id) {
        return accountReconciliationService.reconcileConnection(currentUserService.requireOwnedConnection(id));
    }

    @DeleteMapping("/connections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable UUID id) {
        alpacaOAuthService.disconnectConnection(currentUserService.requireOwnedConnection(id));
    }
}
