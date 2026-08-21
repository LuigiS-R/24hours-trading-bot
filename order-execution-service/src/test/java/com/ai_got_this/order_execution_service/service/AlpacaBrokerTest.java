package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.config.AlpacaProperties;
import com.ai_got_this.order_execution_service.client.AccountServiceClient;
import com.ai_got_this.order_execution_service.dto.OrderEvent;
import com.ai_got_this.order_execution_service.dto.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.math.BigDecimal;

class AlpacaBrokerTest {
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final PublisherService publisherService = mock(PublisherService.class);
    private final AccountServiceClient accountServiceClient = mock(AccountServiceClient.class);
    private final AlpacaProperties properties = new AlpacaProperties();
    private final Order order = new Order();
    private AlpacaBroker broker;

    @BeforeEach
    void setUp() {
        properties.setOrderEndpoint("https://broker.invalid/v2/orders");
        order.setSymbol("AAPL");
        order.setQuantity(3);
        order.setSide("buy");
        order.setType("limit");
        order.setLimitPrice(BigDecimal.valueOf(123.45));
        order.setConnectionId("cdb06f0b-e7ea-4a24-832e-23d9d7fd6f64");
        broker = new AlpacaBroker(restTemplate, new ObjectMapper(), properties, publisherService, accountServiceClient);
    }

    @Test
    void submitsTheOriginalOrderToTheConfiguredEndpointOnSuccessfulResponse() {
        when(restTemplate.exchange(eq(properties.getOrderEndpoint()), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {
                          "id":"a5be8f5e-fdfa-41f5-a644-7a74fe947a8f",
                          "client_order_id":"client-123",
                          "symbol":"AAPL",
                          "side":"buy",
                          "qty":"3",
                          "type":"limit",
                          "time_in_force":"day",
                          "status":"accepted",
                          "created_at":"2026-08-20T00:00:00Z",
                          "updated_at":"2026-08-20T00:00:00Z"
                        }
                        """));
        when(accountServiceClient.brokerCredential(java.util.UUID.fromString(order.getConnectionId())))
                .thenReturn(new AccountServiceClient.BrokerCredential(
                        java.util.UUID.fromString(order.getConnectionId()), "oauth-access-token"));

        broker.placeOrder(order);

        ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(properties.getOrderEndpoint()), eq(HttpMethod.POST), request.capture(), eq(String.class));
        assertEquals(Map.of(
                "symbol", "AAPL",
                "qty", "3",
                "side", "buy",
                "type", "limit",
                "limit_price", "123.45",
                "time_in_force", "day"
        ), request.getValue().getBody());
        assertEquals("Bearer oauth-access-token", request.getValue().getHeaders().getFirst("Authorization"));
        assertEquals("application/json", request.getValue().getHeaders().getContentType().toString());
        verify(publisherService).publishOrderEvent(argThat(event ->
                "HTTP".equals(event.getStream())
                        && "create".equals(event.getEvent())
                        && order.getConnectionId().equals(event.getConnectionId())
                        && "a5be8f5e-fdfa-41f5-a644-7a74fe947a8f".equals(event.getOrder().getId())));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 422, 429, 500})
    void doesNotRetryOrConvertNonSuccessfulBrokerResponsesIntoSuccess(int status) {
        stubBrokerCredential();
        when(restTemplate.exchange(eq(properties.getOrderEndpoint()), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(status).body("{\"error\":\"rejected\"}"));

        assertDoesNotThrow(() -> broker.placeOrder(order));

        verify(restTemplate).exchange(eq(properties.getOrderEndpoint()), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void containsRestClientFailuresAtTheBrokerBoundary() {
        stubBrokerCredential();
        when(restTemplate.exchange(eq(properties.getOrderEndpoint()), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("network unavailable"));

        assertDoesNotThrow(() -> broker.placeOrder(order));

        verify(restTemplate).exchange(eq(properties.getOrderEndpoint()), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void doesNotExposeBrokerFailureContentsWhenTheBrokerClientFails() {
        stubBrokerCredential();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Logger logger = (Logger) LoggerFactory.getLogger(AlpacaBroker.class);
        appender.start();
        logger.addAppender(appender);
        try {
            when(restTemplate.exchange(eq(properties.getOrderEndpoint()), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RestClientException("request rejected: test-only-secret"));

            broker.placeOrder(order);

            assertFalse(appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("test-only-secret")));
        }
        finally {
            logger.detachAppender(appender);
        }
    }

    private void stubBrokerCredential() {
        when(accountServiceClient.brokerCredential(java.util.UUID.fromString(order.getConnectionId())))
                .thenReturn(new AccountServiceClient.BrokerCredential(
                        java.util.UUID.fromString(order.getConnectionId()), "oauth-access-token"));
    }
}
