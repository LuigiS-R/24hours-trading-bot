package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.dto.Order;
import com.ai_got_this.order_execution_service.dto.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class AlpacaBroker implements Broker{
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    public AlpacaBroker(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void placeOrder(Order order) {
        String url = "https://paper-api.alpaca.markets/v2/orders";
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, order, String.class);
            HttpStatusCode statusCode = response.getStatusCode();
            String body = response.getBody();

            if(statusCode.is2xxSuccessful()) {
                OrderResponse orderResponse = objectMapper.readValue(body, OrderResponse.class);
                log.info("[Alpaca Broker] Order placed successfully. Client Order ID: {}", orderResponse.getClientOrderId());
            }
            else{
                if (statusCode.value() == 403){
                    log.error("[Alpaca Broker] Failed to place order. Buying power or shares is not sufficient.");
                }
                else if (statusCode.value() == 422){
                    log.error("[Alpaca Broker] Failed to place order. Invalid order parameters.");
                }
                else{
                    log.error("[Alpaca Broker] Failed to place order. HTTP Status Code: {}, Response Body: {}", statusCode.value(), body);
                }
            }
        }
        catch (RestClientException e) {
            log.error("[Alpaca Broker] Failed to perform HTTP request to the broker. Detailed Explanation: [{}]", e.getMessage());
        }
    }
}
