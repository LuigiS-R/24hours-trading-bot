package com.ai_got_this.market_data_service.service;

import com.ai_got_this.market_data_service.dto.MarketData;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;

class MarketDataPublisherTest {
    @Test
    void publishesSuppliedMarketDataToSuppliedExchangeAndRoutingKey() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        MarketData marketData = MarketData.builder().ticket("AAPL").close(100.0).build();

        new MarketDataPublisher(template).publishMarketData("market-data-exchange", marketData, "market-data");

        verify(template).convertAndSend("market-data-exchange", "market-data", marketData);
    }

    @Test
    void brokerFailureIsNotReportedAsSuccessfulByPublisher() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doThrow(new IllegalStateException("broker unavailable")).when(template)
                .convertAndSend(eq("market-data-exchange"), eq("market-data"), isNull(Object.class));

        assertThrows(IllegalStateException.class,
                () -> new MarketDataPublisher(template).publishMarketData("market-data-exchange", null, "market-data"));
    }
}
