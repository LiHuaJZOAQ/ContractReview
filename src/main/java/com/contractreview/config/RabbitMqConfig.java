package com.contractreview.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String QUEUE_REVIEW = "contract.review.queue";
    public static final String QUEUE_DLX = "contract.review.dlx";
    public static final String EXCHANGE_REVIEW = "contract.review.exchange";
    public static final String EXCHANGE_DLX = "contract.review.dlx.exchange";
    public static final String ROUTING_KEY = "contract.review.routing";
    public static final String ROUTING_KEY_DLX = "contract.review.dlx.routing";

    @Bean
    public Queue reviewQueue() {
        return QueueBuilder.durable(QUEUE_REVIEW)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DLX)
                .build();
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(QUEUE_DLX).build();
    }

    @Bean
    public DirectExchange reviewExchange() {
        return new DirectExchange(EXCHANGE_REVIEW);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(EXCHANGE_DLX);
    }

    @Bean
    public Binding reviewBinding() {
        return BindingBuilder.bind(reviewQueue())
                .to(reviewExchange())
                .with(ROUTING_KEY);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue())
                .to(dlxExchange())
                .with(ROUTING_KEY_DLX);
    }
}
