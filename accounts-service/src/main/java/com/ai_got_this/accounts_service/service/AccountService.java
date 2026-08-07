package com.ai_got_this.accounts_service.service;

import com.ai_got_this.accounts_service.dto.GetAccountResponse;
import com.ai_got_this.accounts_service.entity.Account;
import com.ai_got_this.accounts_service.repo.AccountRepository;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    AccountRepository accountRepository;
    AlpacaService brokerService;

    private final String clientId = "";
    private final String clientSecret = "";

    public AccountService(AccountRepository accountRepository, AlpacaService alpacaService ) {
        this.accountRepository = accountRepository;
        this.brokerService = alpacaService;
    }

    public boolean hasAnyAccounts(String userId) {
        return accountRepository.existsById(userId);
    }

    public void createAccount(String userId) {
        GetAccountResponse accountResponse = brokerService.getAccount(clientId, clientSecret);
        Account account = Account.builder()
                .userId(userId)
                .brokerAccountId(accountResponse.getAccountNumber())
                .isPaper(true)
                .cashBalance(accountResponse.getCash())
                .currency(accountResponse.getCurrency())
                .createdAt(java.time.OffsetDateTime.now())
                .updatedAt(java.time.OffsetDateTime.now())
                .build();
        accountRepository.save(account);
    }

    public Account getAccount(String userId) {
        return accountRepository.findById(userId).orElse(null);
    }
}
