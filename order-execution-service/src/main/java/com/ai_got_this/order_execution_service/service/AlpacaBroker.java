package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.config.AlpacaProperties;
import com.ai_got_this.order_execution_service.dto.Order;
import com.ai_got_this.order_execution_service.dto.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class AlpacaBroker implements Broker{
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private AlpacaProperties alpacaProperties;

    public AlpacaBroker(RestTemplate restTemplate, ObjectMapper objectMapper, AlpacaProperties alpacaProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.alpacaProperties = alpacaProperties;
    }

    @Override
    public void placeOrder(Order order) {
        String url = alpacaProperties.getOrderEndpoint();
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(toAlpacaOrderRequest(order), requestHeaders()),
                    String.class
            );
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

    private HttpHeaders requestHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("APCA-API-KEY-ID", alpacaProperties.getKey());
        headers.set("APCA-API-SECRET-KEY", alpacaProperties.getSecret());
        return headers;
    }

    private Map<String, String> toAlpacaOrderRequest(Order order) {
        Map<String, String> request = new LinkedHashMap<>();
        request.put("symbol", order.getSymbol());
        request.put("qty", String.valueOf(order.getQuantity()));
        request.put("side", order.getSide());
        request.put("type", order.getType());
        request.put("time_in_force", order.getTimeInForce());
        return request;
    }
}
