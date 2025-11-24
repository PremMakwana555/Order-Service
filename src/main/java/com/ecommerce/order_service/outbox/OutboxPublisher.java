package com.ecommerce.order_service.outbox;

import com.ecommerce.order_service.domain.entity.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Background publisher for processing outbox events and publishing to Kafka.
 * Runs periodically to ensure eventual consistency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Periodically publish unpublished events from the outbox.
     * Runs every 5 seconds by default.
     */
    @Scheduled(fixedDelay = 5000)
    public void publishEvents() {
        List<OutboxEvent> unpublishedEvents = outboxService.getUnpublishedEvents();

        if (unpublishedEvents.isEmpty()) {
            return;
        }

        log.debug("Publishing {} outbox events", unpublishedEvents.size());

        for (OutboxEvent event : unpublishedEvents) {
            try {
                publishEvent(event);
                outboxService.markAsPublished(event.getId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event: {}", event.getId(), e);
                // Event remains unpublished and will be retried
            }
        }
    }

    private void publishEvent(OutboxEvent event) throws Exception {
        String topic = getTopicForEventType(event.getEventType());
        // payload is stored as JSON already in the OutboxEvent
        String payloadJson = event.getPayload();

        Message<String> message = MessageBuilder
                .withPayload(payloadJson)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader(KafkaHeaders.KEY, event.getAggregateId())
                .setHeader("eventType", event.getEventType())
                .setHeader("aggregateType", event.getAggregateType())
                .setHeader("aggregateId", event.getAggregateId())
                .setHeader(KafkaHeaders.CONTENT_TYPE, "application/json")
                .build();

        kafkaTemplate.send(message);
        log.info("Published event {} to topic {}: {}/{}",
                event.getEventType(), topic, event.getAggregateType(), event.getAggregateId());
    }

    private String getTopicForEventType(String eventType) {
        // Map event types to Kafka topics
        return switch (eventType) {
            case "OrderCreated", "OrderConfirmed", "OrderCancelled" -> "orders.events";
            case "PaymentRequested" -> "payments.commands";
            case "NotificationRequested" -> "notifications.commands";
            default -> "orders.events";
        };
    }

    /**
     * Cleanup old published events daily.
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    public void cleanupOldEvents() {
        log.info("Starting outbox event cleanup");
        outboxService.cleanupOldEvents(7); // Keep events for 7 days
    }
}
