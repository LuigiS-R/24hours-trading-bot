package com.ai_got_this.market_data_service.service;

import com.ai_got_this.market_data_service.dto.MarketData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class MarketDataPublisher {
    private final RabbitTemplate rabbitTemplate;

    public MarketDataPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishMarketData(String exchange, MarketData marketData, String routingKey) {
        rabbitTemplate.convertAndSend(exchange, routingKey, marketData);
    }
}
