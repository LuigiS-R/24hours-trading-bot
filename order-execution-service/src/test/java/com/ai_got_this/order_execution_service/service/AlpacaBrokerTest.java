package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.config.AlpacaProperties;
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
import org.mockito.ArgumentCaptor;

import java.util.Map;

class AlpacaBrokerTest {
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final AlpacaProperties properties = new AlpacaProperties();
    private final Order order = new Order();
    private AlpacaBroker broker;

    @BeforeEach
    void setUp() {
        properties.setOrderEndpoint("https://broker.invalid/v2/orders");
        properties.setKey("key");
        properties.setSecret("secret");
        order.setSymbol("AAPL");
        order.setQuantity(3);
        order.setSide("buy");
        order.setTimeInForce("day");
        broker = new AlpacaBroker(restTemplate, new ObjectMapper(), properties);
    }

    @Test
    void submitsTheOriginalOrderToTheConfiguredEndpointOnSuccessfulResponse() {
        when(restTemplate.exchange(eq(properties.getOrderEndpoint()), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"clientOrderId\":\"client-123\"}"));

        broker.placeOrder(order);

        ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(properties.getOrderEndpoint()), eq(HttpMethod.POST), request.capture(), eq(String.class));
        assertEquals(Map.of(
                "symbol", "AAPL",
                "qty", "3",
                "side", "buy",
                "type", "market",
                "time_in_force", "day"
        ), request.getValue().getBody());
        assertEquals("key", request.getValue().getHeaders().getFirst("APCA-API-KEY-ID"));
        assertEquals("secret", request.getValue().getHeaders().getFirst("APCA-API-SECRET-KEY"));
        assertEquals("application/json", request.getValue().getHeaders().getContentType().toString());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 422, 429, 500})
    void doesNotRetryOrConvertNonSuccessfulBrokerResponsesIntoSuccess(int status) {
        when(restTemplate.exchange(eq(properties.getOrderEndpoint()), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(status).body("{\"error\":\"rejected\"}"));

        assertDoesNotThrow(() -> broker.placeOrder(order));

        verify(restTemplate).exchange(eq(properties.getOrderEndpoint()), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void containsRestClientFailuresAtTheBrokerBoundary() {
        when(restTemplate.exchange(eq(properties.getOrderEndpoint()), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("network unavailable"));

        assertDoesNotThrow(() -> broker.placeOrder(order));

        verify(restTemplate).exchange(eq(properties.getOrderEndpoint()), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void doesNotExposeConfiguredCredentialsWhenTheBrokerClientFails() {
        properties.setSecret("test-only-secret");
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
}
