package com.ai_got_this.accounts_service.service;

import com.ai_got_this.accounts_service.dto.GetAccountResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class AlpacaService implements BrokerService {
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    public AlpacaService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public GetAccountResponse getAccount(String keyId, String keySecret) {
        String url = "https://paper-api.alpaca.markets/v2/account";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            HttpStatusCode statusCode = response.getStatusCode();
            String body = response.getBody();

            if(statusCode.is2xxSuccessful()) {
                GetAccountResponse getAccountResponse = objectMapper.readValue(body, GetAccountResponse.class);
                log.info("[Alpaca Broker] Account details retrieved successfully. Account ID: {}", getAccountResponse.getAccountNumber());
                return getAccountResponse;
            }
            else{
                log.error("[Alpaca Broker] Failed to retrieve account details. HTTP Status Code: {}, Response Body: {}", statusCode.value(), body);
                return null;
            }
        }
        catch (RestClientException e) {
            log.error("[Alpaca Broker] Failed to perform HTTP request to the broker. Detailed Explanation: [{}]", e.getMessage());
            return null;
        }
    }
}
