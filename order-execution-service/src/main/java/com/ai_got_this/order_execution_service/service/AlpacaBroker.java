package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.config.AlpacaProperties;
import com.ai_got_this.order_execution_service.dto.Order;
import com.ai_got_this.order_execution_service.dto.OrderEvent;
import com.ai_got_this.order_execution_service.dto.TradeUpdateOrder;
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
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class AlpacaBroker implements Broker{
    private static final String DEFAULT_TIME_IN_FORCE = "day";
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private AlpacaProperties alpacaProperties;
    private PublisherService publisherService;

    public AlpacaBroker(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            AlpacaProperties alpacaProperties,
            PublisherService publisherService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.alpacaProperties = alpacaProperties;
        this.publisherService = publisherService;
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
                publishOrderCreatedEvent(order, body);
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
            log.error("[Alpaca Broker] Failed to perform HTTP request to the broker.");
        }
    }

    private void publishOrderCreatedEvent(Order order, String responseBody) {
        try {
            TradeUpdateOrder alpacaOrder = objectMapper.readValue(responseBody, TradeUpdateOrder.class);
            if (alpacaOrder == null || alpacaOrder.getId() == null || alpacaOrder.getId().isBlank()) {
                log.error("[Alpaca Broker] Order was accepted but Alpaca returned no order ID.");
                return;
            }
            publisherService.publishOrderEvent(OrderEvent.httpOrderCreated(order.getConnectionId(), alpacaOrder));
            log.info("[Alpaca Broker] Order placed successfully. Client Order ID: {}", alpacaOrder.getClientOrderId());
        } catch (Exception exception) {
            // Do not re-submit an accepted Alpaca order if publishing its creation event fails.
            log.error("[Alpaca Broker] Order was accepted but its creation event could not be published.");
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
        request.put("side", order.getSide().trim().toLowerCase(Locale.ROOT));
        request.put("type", order.getType().trim().toLowerCase(Locale.ROOT));
        request.put("limit_price", order.getLimitPrice().toPlainString());
        // Alpaca requires time_in_force. It is intentionally no longer an
        // inbound order field; Order Execution owns the conservative default.
        request.put("time_in_force", DEFAULT_TIME_IN_FORCE);
        return request;
    }
}
