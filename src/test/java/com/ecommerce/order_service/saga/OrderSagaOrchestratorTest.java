package com.ecommerce.order_service.saga;

import com.ecommerce.order_service.domain.entity.OrderSaga;
import com.ecommerce.order_service.domain.entity.OrderStatus;
import com.ecommerce.order_service.domain.entity.SagaState;
import com.ecommerce.order_service.domain.repository.OrderSagaRepository;
import com.ecommerce.order_service.kafka.event.NotificationRequestedCommand;
import com.ecommerce.order_service.kafka.event.PaymentFailedEvent;
import com.ecommerce.order_service.kafka.event.PaymentRequestCommand;
import com.ecommerce.order_service.kafka.event.PaymentSucceededEvent;
import com.ecommerce.order_service.outbox.OutboxService;
import com.ecommerce.order_service.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock
    private OrderSagaRepository sagaRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderSagaOrchestrator sagaOrchestrator;

    private OrderSaga saga;
    private final String SAGA_ID = "saga-123";
    private final String ORDER_ID = "order-123";
    private final String USER_ID = "user-123";
    private final String CORRELATION_ID = "corr-123";

    @BeforeEach
    void setUp() {
        saga = OrderSaga.builder()
                .sagaId(SAGA_ID)
                .orderId(ORDER_ID)
                .state(SagaState.STARTED)
                .payload("{}")
                .build();
    }

    @Test
    void startPaymentRequest_shouldUpdateStateAndPublishCommand() {
        // Given
        when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        BigDecimal amount = new BigDecimal("100.00");

        // When
        sagaOrchestrator.startPaymentRequest(SAGA_ID, ORDER_ID, USER_ID, amount, CORRELATION_ID);

        // Then
        verify(sagaRepository).save(saga);
        assertThat(saga.getState()).isEqualTo(SagaState.PAYMENT_REQUESTED);
        verify(orderService).updateOrderStatus(ORDER_ID, OrderStatus.PAYMENT_REQUESTED);

        ArgumentCaptor<PaymentRequestCommand> captor = ArgumentCaptor.forClass(PaymentRequestCommand.class);
        verify(outboxService).saveEvent(eq("Order"), eq(ORDER_ID), eq("PaymentRequested"), captor.capture());

        PaymentRequestCommand command = captor.getValue();
        assertThat(command.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(command.getAmount()).isEqualTo(amount);
        assertThat(command.getCorrelationId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    void handlePaymentSuccess_shouldConfirmOrderAndPublishNotification() {
        // Given
        PaymentSucceededEvent event = PaymentSucceededEvent.builder()
                .sagaId(SAGA_ID)
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .paymentId("pay-123")
                .correlationId(CORRELATION_ID)
                .timestamp(LocalDateTime.now())
                .build();

        when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));

        // When
        sagaOrchestrator.handlePaymentSuccess(event);

        // Then
        verify(sagaRepository, times(2)).save(saga); // Once for PAYMENT_SUCCEEDED, once for COMPLETED
        assertThat(saga.getState()).isEqualTo(SagaState.COMPLETED);

        verify(orderService).updateOrderStatus(ORDER_ID, OrderStatus.CONFIRMED);
        verify(orderService).updateOrderPaymentId(ORDER_ID, "pay-123");

        // Verify OrderConfirmed event
        verify(outboxService).saveEvent(eq("Order"), eq(ORDER_ID), eq("OrderConfirmed"), any());

        // Verify NotificationRequested command
        ArgumentCaptor<NotificationRequestedCommand> notifCaptor = ArgumentCaptor.forClass(NotificationRequestedCommand.class);
        verify(outboxService).saveEvent(eq("Order"), eq(ORDER_ID), eq("NotificationRequested"), notifCaptor.capture());

        NotificationRequestedCommand notifCommand = notifCaptor.getValue();
        assertThat(notifCommand.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(notifCommand.getNotificationType()).isEqualTo("ORDER_CONFIRMED");
    }

    @Test
    void handlePaymentFailure_shouldCompensateOrder() {
        // Given
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .sagaId(SAGA_ID)
                .orderId(ORDER_ID)
                .reason("Insufficient funds")
                .correlationId(CORRELATION_ID)
                .timestamp(LocalDateTime.now())
                .build();

        when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));

        // When
        sagaOrchestrator.handlePaymentFailure(event);

        // Then
        verify(sagaRepository, times(2)).save(saga); // Once for PAYMENT_FAILED, once for COMPENSATED
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATED);

        verify(orderService).updateOrderStatus(ORDER_ID, OrderStatus.CANCELLED);
        verify(outboxService).saveEvent(eq("Order"), eq(ORDER_ID), eq("OrderCancelled"), any());
    }
}
