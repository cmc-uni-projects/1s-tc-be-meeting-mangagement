package com.cmc.meeting.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_NAME = "meeting.user.sync.queue";
    public static final String EXCHANGE_NAME = "user.exchange";
    public static final String ROUTING_KEY = "user.created";

    @Bean
    public Queue userQueue() {
        return new Queue(QUEUE_NAME, true); // true = durable (bền vững)
    }

    @Bean
    public TopicExchange userExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    // Binding: Nối Queue vào Exchange
    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter converter() {
        return new Jackson2JsonMessageConverter();
    }
}