package com.jojo.prompt.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.jojo.prompt.common.constant.PromptMqConstant.*;

@Configuration

public class RabbitMqConfig {

    @Bean
    public MessageConverter jasonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange promptReviewExchange() {
        return new DirectExchange(PROMPT_REVIEW_EXCHANGE, true, false);
    }

    @Bean
    public Queue promptReviewQueue() {
        return QueueBuilder.durable(PROMPT_REVIEW_QUEUE)
                .deadLetterExchange(PROMPT_REVIEW_DLX_EXCHANGE)
                .deadLetterRoutingKey(PROMPT_REVIEW_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding promptReviewBinding() {
        return BindingBuilder.bind(promptReviewQueue())
                .to(promptReviewExchange())
                .with(PROMPT_REVIEW_ROUTING_KEY);
    }

    @Bean
    public DirectExchange promptReviewDlxExchange() {
        return new DirectExchange(PROMPT_REVIEW_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue promptReviewDlxQueue() {
        return QueueBuilder.durable(PROMPT_REVIEW_DLX_QUEUE).build();
    }

    @Bean
    public Binding promptReviewDlxBinding() {
        return BindingBuilder.bind(promptReviewDlxQueue())
                .to(promptReviewDlxExchange())
                .with(PROMPT_REVIEW_DLX_ROUTING_KEY);
    }

    @Bean
    public DirectExchange promptCountDelayExchange() {
        return new DirectExchange(PROMPT_COUNT_DELAY_EXCHANGE, true, false);
    }

    @Bean
    public Queue promptCountDelayQueue() {
        return QueueBuilder.durable(PROMPT_COUNT_DELAY_QUEUE)
                .deadLetterExchange(PROMPT_COUNT_SYNC_EXCHANGE)
                .deadLetterRoutingKey(PROMPT_COUNT_SYNC_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding promptCountDelayBinding() {
        return BindingBuilder.bind(promptCountDelayQueue())
                .to(promptCountDelayExchange())
                .with(PROMPT_COUNT_DELAY_ROUTING_KEY);
    }

    @Bean
    public DirectExchange promptCountSyncExchange() {
        return new DirectExchange(PROMPT_COUNT_SYNC_EXCHANGE, true, false);
    }

    @Bean
    public Queue promptCountSyncQueue() {
        return QueueBuilder.durable(PROMPT_COUNT_SYNC_QUEUE)
                .deadLetterExchange(PROMPT_COUNT_SYNC_DLX_EXCHANGE)
                .deadLetterRoutingKey(PROMPT_COUNT_SYNC_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding promptCountSyncBinding() {
        return BindingBuilder.bind(promptCountSyncQueue())
                .to(promptCountSyncExchange())
                .with(PROMPT_COUNT_SYNC_ROUTING_KEY);
    }

    @Bean
    public DirectExchange promptCountSyncDlxExchange() {
        return new DirectExchange(PROMPT_COUNT_SYNC_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue promptCountSyncDlxQueue() {
        return QueueBuilder.durable(PROMPT_COUNT_SYNC_DLX_QUEUE).build();
    }

    @Bean
    public Binding promptCountSyncDlxBinding() {
        return BindingBuilder.bind(promptCountSyncDlxQueue())
                .to(promptCountSyncDlxExchange())
                .with(PROMPT_COUNT_SYNC_DLX_ROUTING_KEY);
    }

}
