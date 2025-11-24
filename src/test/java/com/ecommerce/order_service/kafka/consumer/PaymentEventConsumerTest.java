package com.ecommerce.order_service.kafka.consumer;

import com.ecommerce.order_service.kafka.event.PaymentFailedEvent;
import com.ecommerce.order_service.kafka.event.PaymentSucceededEvent;
import com.ecommerce.order_service.saga.OrderSagaOrchestrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private OrderSagaOrchestrator sagaOrchestrator;

    @InjectMocks
    private PaymentEventConsumer paymentEventConsumer;

    @Test
    void handlePaymentEvent_shouldProcessPaymentSucceeded() {
        // Given
        PaymentSucceededEvent event = PaymentSucceededEvent.builder()
                .sagaId("saga-1")
                .orderId("order-1")
                .paymentId("pay-1")
                .correlationId("corr-1")
                .timestamp(LocalDateTime.now())
                .build();

        // When
        paymentEventConsumer.handlePaymentEvent(event, "PaymentSucceeded", "key-1");

        // Then
        verify(sagaOrchestrator).handlePaymentSuccess(any(PaymentSucceededEvent.class));
    }

    @Test
    void handlePaymentEvent_shouldProcessPaymentFailed() {
        // Given
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .sagaId("saga-1")
                .orderId("order-1")
                .reason("Insufficient funds")
                .correlationId("corr-1")
                .timestamp(LocalDateTime.now())
                .build();

        // When
        paymentEventConsumer.handlePaymentEvent(event, "PaymentFailed", "key-1");

        // Then
        verify(sagaOrchestrator).handlePaymentFailure(any(PaymentFailedEvent.class));
    }
}
