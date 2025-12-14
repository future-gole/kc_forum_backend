package com.doublez.kc_forum.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "forum.event.exchange";
    public static final String LIKE_QUEUE_NAME = "forum.like.persistence.queue";
    public static final String VIEW_QUEUE_NAME = "forum.view.persistence.queue";
    public static final String LIKE_ROUTING_KEY = "event.like";
    public static final String VIEW_ROUTING_KEY = "event.view";

    @Bean
    public DirectExchange forumEventExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue likePersistenceQueue() {
        return new Queue(LIKE_QUEUE_NAME, true);
    }

    @Bean
    public Queue viewPersistenceQueue() {
        return new Queue(VIEW_QUEUE_NAME, true);
    }

    @Bean
    public Binding likeBinding(Queue likePersistenceQueue, DirectExchange forumEventExchange) {
        return BindingBuilder.bind(likePersistenceQueue).to(forumEventExchange).with(LIKE_ROUTING_KEY);
    }

    @Bean
    public Binding viewBinding(Queue viewPersistenceQueue, DirectExchange forumEventExchange) {
        return BindingBuilder.bind(viewPersistenceQueue).to(forumEventExchange).with(VIEW_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
