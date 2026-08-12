package com.ai_got_this.accounts_service.service;

import com.ai_got_this.accounts_service.dto.GetAccountResponse;
import com.ai_got_this.accounts_service.entity.Account;
import com.ai_got_this.accounts_service.exception.AccountNotFoundException;
import com.ai_got_this.accounts_service.repo.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;

@Slf4j
@Service
public class AccountService {
    AccountRepository accountRepository;
    AlpacaService brokerService;
    RestTemplate restTemplate;
    ObjectMapper objectMapper;

    private final String clientId = "";
    private final String clientSecret = "";

    public AccountService(AccountRepository accountRepository, AlpacaService alpacaService, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.brokerService = alpacaService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean hasAnyAccounts(String userId) {
        return accountRepository.existsById(userId);
    }

    public void createAccount(String userId) {
        GetAccountResponse accountResponse = brokerService.getAccount(clientId, clientSecret);
        createAccount(accountResponse, userId, "alpaca");
    }


    public GetAccountResponse getAccountFromBroker(String authToken) {
        String endpoint = "https://placeholder-broker-endpoint/accounts";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + authToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(endpoint, HttpMethod.GET, request, String.class);
            HttpStatusCode statusCode = response.getStatusCode();
            String responseBody = response.getBody();


            if(statusCode.is2xxSuccessful()) {
                GetAccountResponse getAccountResponse = objectMapper.readValue(responseBody, GetAccountResponse.class);
                log.info("[Alpaca Broker] Account information retrieved successfully. Account Number: {}", getAccountResponse.getAccountNumber());
                return getAccountResponse;
            }
            else{
                log.error("[Alpaca Broker] Failed to place order. HTTP Status Code: {}, Response Body: {}", statusCode.value(), responseBody);
                return null;
            }
        }
        catch (RestClientException e) {
            log.error("[Alpaca Broker] Failed to perform HTTP request to the broker. Detailed Explanation: [{}]", e.getMessage());
            return null;
        }
        catch (RuntimeException e) {
            log.error("Failed to parse broker getAccount response: {}", e.getMessage());
            return null;
        }
    }

    public Account createAccount(GetAccountResponse getAccountResponse, String userId, String brokerName) {
        OffsetDateTime createdAt = OffsetDateTime.parse(getAccountResponse.getCreatedAt());

        Account account = Account.builder()
                .userId(userId)
                .brokerName(brokerName)
                .brokerAccountId(getAccountResponse.getAccountNumber())
                .isPaper(true)
                .cashBalance(getAccountResponse.getCash())
                .currency(getAccountResponse.getCurrency())
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();

        return accountRepository.save(account);
    }

    public Account getAccount(String authToken, String userId, String brokerName) {
        Account account = accountRepository.findByUserId(userId);
        if (account != null) {
            return account;
        }

        GetAccountResponse brokerAccount = getAccountFromBroker(authToken);
        if (brokerAccount == null) {
            throw new AccountNotFoundException("Account not found for userId: " + userId + " in broker: " + brokerName);
        }

        return createAccount(brokerAccount, userId, brokerName);
    }
}
