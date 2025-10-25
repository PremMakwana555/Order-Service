package com.ecommerce.order_service.kafka.consumer;

import com.ecommerce.order_service.kafka.event.PaymentFailedEvent;
import com.ecommerce.order_service.kafka.event.PaymentSucceededEvent;
import com.ecommerce.order_service.saga.OrderSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for payment events.
 * Listens to payment events and coordinates with saga orchestrator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final OrderSagaOrchestrator sagaOrchestrator;

    /**
     * Handle payment succeeded events.
     */
    @KafkaListener(topics = "${kafka.topics.payments-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void handlePaymentEvent(@Payload Object payload,
                                   @Header(value = "eventType", required = false) String eventType,
                                   @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {

        log.info("Received payment event of type: {} with key: {}", eventType, key);

        try {
            if ("PaymentSucceeded".equals(eventType)) {
                PaymentSucceededEvent event = convertPayload(payload, PaymentSucceededEvent.class);
                setMDC(event.getCorrelationId(), event.getSagaId());
                sagaOrchestrator.handlePaymentSuccess(event);
            } else if ("PaymentFailed".equals(eventType)) {
                PaymentFailedEvent event = convertPayload(payload, PaymentFailedEvent.class);
                setMDC(event.getCorrelationId(), event.getSagaId());
                sagaOrchestrator.handlePaymentFailure(event);
            } else {
                log.warn("Unknown payment event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing payment event: {}", eventType, e);
            // In production, consider dead letter queue
            throw e;
        } finally {
            clearMDC();
        }
    }

    private <T> T convertPayload(Object payload, Class<T> targetClass) {
        if (targetClass.isInstance(payload)) {
            return targetClass.cast(payload);
        }
        throw new IllegalArgumentException("Cannot convert payload to " + targetClass.getName());
    }

    private void setMDC(String correlationId, String sagaId) {
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        if (sagaId != null) {
            MDC.put("sagaId", sagaId);
        }
    }

    private void clearMDC() {
        MDC.clear();
    }
}

