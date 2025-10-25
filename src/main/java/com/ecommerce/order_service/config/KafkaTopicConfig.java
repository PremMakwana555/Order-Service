package com.ecommerce.order_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration.
 * Creates required topics on application startup.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topics.orders-events}")
    private String ordersEventsTopic;

    @Value("${kafka.topics.orders-commands}")
    private String ordersCommandsTopic;

    @Value("${kafka.topics.payments-commands}")
    private String paymentsCommandsTopic;

    @Value("${kafka.topics.payments-events}")
    private String paymentsEventsTopic;

    @Value("${kafka.topics.notifications-commands}")
    private String notificationsCommandsTopic;

    @Bean
    public NewTopic ordersEventsTopic() {
        return TopicBuilder.name(ordersEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ordersCommandsTopic() {
        return TopicBuilder.name(ordersCommandsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentsCommandsTopic() {
        return TopicBuilder.name(paymentsCommandsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentsEventsTopic() {
        return TopicBuilder.name(paymentsEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationsCommandsTopic() {
        return TopicBuilder.name(notificationsCommandsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

