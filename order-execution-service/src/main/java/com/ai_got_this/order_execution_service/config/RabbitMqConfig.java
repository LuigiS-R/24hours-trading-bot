package com.ai_got_this.order_execution_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RabbitMqConfig {
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    //Method returning a queue bean. Set the queue name as a placeholder
    @Bean
    public Queue orderEventsPortfolioQueue() {
        return new Queue("order-events-portfolio", true);
    }

    @Bean
    public Queue orderEventsNotificationQueue() {
        return new Queue("order-events-notification", true);
    }

    //Method returning a fanout exchange bean. Set the exchange name as a placeholder
    @Bean
    public FanoutExchange orderEventsFanoutExchange() {
        return new FanoutExchange("order-events-fanout", true, false);
    }

    //Bind both queues to the fanout exchange
    @Bean
    public Binding orderEventsPortfolioBinding(Queue orderEventsPortfolioQueue, FanoutExchange orderEventsFanoutExchange) {
        return BindingBuilder.bind(orderEventsPortfolioQueue).to(orderEventsFanoutExchange);
    }

    @Bean
    public Binding orderEventsNotificationBinding(Queue orderEventsNotificationQueue, FanoutExchange orderEventsFanoutExchange) {
        return BindingBuilder.bind(orderEventsNotificationQueue).to(orderEventsFanoutExchange);
    }
}
