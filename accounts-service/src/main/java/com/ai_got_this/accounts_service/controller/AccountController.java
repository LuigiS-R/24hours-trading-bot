package com.ai_got_this.accounts_service.controller;

import com.ai_got_this.accounts_service.entity.Account;
import com.ai_got_this.accounts_service.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {
    private AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/portfolio")
    public ResponseEntity<Account> getPortfolio(@RequestParam String userId) {
        Account account = accountService.getAccount(userId);
        return ResponseEntity.ok(account);
    }
}
