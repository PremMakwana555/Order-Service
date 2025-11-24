package com.ecommerce.order_service.saga;

import com.ecommerce.order_service.domain.entity.OrderSaga;
import com.ecommerce.order_service.domain.entity.OrderStatus;
import com.ecommerce.order_service.domain.entity.SagaState;
import com.ecommerce.order_service.domain.repository.OrderSagaRepository;
import com.ecommerce.order_service.kafka.event.*;
import com.ecommerce.order_service.outbox.OutboxService;
import com.ecommerce.order_service.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Saga Orchestrator for managing order workflow.
 * Coordinates between Order and Payment services using saga pattern.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final OrderSagaRepository sagaRepository;
    private final OrderService orderService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    /**
     * Start the saga by requesting payment.
     * Triggered after order creation.
     */
    @Transactional
    public void startPaymentRequest(String sagaId, String orderId, String userId,
                                    java.math.BigDecimal amount, String correlationId) {
        log.info("Starting payment request for saga: {}, order: {}", sagaId, orderId);

        // Update saga state
        OrderSaga saga = sagaRepository.findById(sagaId)
                .orElseThrow(() -> new RuntimeException("Saga not found: " + sagaId));
        saga.setState(SagaState.PAYMENT_REQUESTED);
        sagaRepository.save(saga);

        // Update order status
        orderService.updateOrderStatus(orderId, OrderStatus.PAYMENT_REQUESTED);

        // Publish payment request command
        PaymentRequestCommand command = PaymentRequestCommand.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .correlationId(correlationId)
                .sagaId(sagaId)
                .timestamp(LocalDateTime.now())
                .build();

        outboxService.saveEvent("Order", orderId, "PaymentRequested", command);
        log.info("Payment request published for order: {}", orderId);
    }

    /**
     * Handle successful payment.
     * Complete the saga and confirm the order.
     */
    @Transactional
    public void handlePaymentSuccess(PaymentSucceededEvent event) {
        log.info("Handling payment success for saga: {}, order: {}", event.getSagaId(), event.getOrderId());

        try {
            // Update saga state
            OrderSaga saga = sagaRepository.findById(event.getSagaId())
                    .orElseThrow(() -> new RuntimeException("Saga not found: " + event.getSagaId()));
            saga.setState(SagaState.PAYMENT_SUCCEEDED);
            sagaRepository.save(saga);

            // Update order
            orderService.updateOrderStatus(event.getOrderId(), OrderStatus.CONFIRMED);
            orderService.updateOrderPaymentId(event.getOrderId(), event.getPaymentId());

            // Mark saga as completed
            saga.setState(SagaState.COMPLETED);
            sagaRepository.save(saga);

            // Publish order confirmed event
            OrderConfirmedEvent confirmedEvent = OrderConfirmedEvent.builder()
                    .orderId(event.getOrderId())
                    .userId(event.getUserId())
                    .paymentId(event.getPaymentId())
                    .correlationId(event.getCorrelationId())
                    .sagaId(event.getSagaId())
                    .timestamp(LocalDateTime.now())
                    .build();

            outboxService.saveEvent("Order", event.getOrderId(), "OrderConfirmed", confirmedEvent);
            log.info("Order confirmed successfully: {}", event.getOrderId());

            // Publish notification request
            NotificationRequestedCommand notificationCommand = NotificationRequestedCommand.builder()
                    .orderId(event.getOrderId())
                    .userId(event.getUserId())
                    .notificationType("ORDER_CONFIRMED")
                    .message("Your order " + event.getOrderId() + " has been confirmed.")
                    .correlationId(event.getCorrelationId())
                    .sagaId(event.getSagaId())
                    .timestamp(LocalDateTime.now())
                    .build();

            outboxService.saveEvent("Order", event.getOrderId(), "NotificationRequested", notificationCommand);
            log.info("Notification request published for order: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("Error handling payment success for order: {}", event.getOrderId(), e);
            handleSagaFailure(event.getSagaId(), event.getOrderId(), "Error processing payment success");
        }
    }

    /**
     * Handle payment failure.
     * Compensate by cancelling the order.
     */
    @Transactional
    public void handlePaymentFailure(PaymentFailedEvent event) {
        log.info("Handling payment failure for saga: {}, order: {}", event.getSagaId(), event.getOrderId());

        try {
            // Update saga state
            OrderSaga saga = sagaRepository.findById(event.getSagaId())
                    .orElseThrow(() -> new RuntimeException("Saga not found: " + event.getSagaId()));
            saga.setState(SagaState.PAYMENT_FAILED);
            sagaRepository.save(saga);

            // Compensate: Cancel the order
            compensateOrder(event.getOrderId(), event.getReason(), event.getCorrelationId(), event.getSagaId());

            // Mark saga as compensated
            saga.setState(SagaState.COMPENSATED);
            sagaRepository.save(saga);

            log.info("Order compensated due to payment failure: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("Error handling payment failure for order: {}", event.getOrderId(), e);
            handleSagaFailure(event.getSagaId(), event.getOrderId(), "Error during compensation");
        }
    }

    /**
     * Compensate by cancelling the order.
     */
    private void compensateOrder(String orderId, String reason, String correlationId, String sagaId) {
        log.info("Compensating order: {} due to: {}", orderId, reason);

        // Update order status to cancelled
        orderService.updateOrderStatus(orderId, OrderStatus.CANCELLED);

        // Publish order cancelled event
        OrderCancelledEvent cancelledEvent = OrderCancelledEvent.builder()
                .orderId(orderId)
                .userId("") // Could be retrieved from saga payload if needed
                .reason(reason)
                .correlationId(correlationId)
                .sagaId(sagaId)
                .timestamp(LocalDateTime.now())
                .build();

        outboxService.saveEvent("Order", orderId, "OrderCancelled", cancelledEvent);
    }

    /**
     * Handle saga failure.
     */
    private void handleSagaFailure(String sagaId, String orderId, String reason) {
        log.error("Saga failed for order: {}, reason: {}", orderId, reason);

        OrderSaga saga = sagaRepository.findById(sagaId)
                .orElseThrow(() -> new RuntimeException("Saga not found: " + sagaId));
        saga.setState(SagaState.FAILED);
        sagaRepository.save(saga);

        orderService.updateOrderStatus(orderId, OrderStatus.FAILED);
    }

    /**
     * Recover stuck sagas (for maintenance/monitoring).
     */
    @Transactional
    public void recoverStuckSagas() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        var terminalStates = java.util.List.of(SagaState.COMPLETED, SagaState.COMPENSATED, SagaState.FAILED);

        var stuckSagas = sagaRepository.findByStateNotInAndLastUpdatedBefore(terminalStates, threshold);

        log.info("Found {} stuck sagas to recover", stuckSagas.size());

        for (OrderSaga saga : stuckSagas) {
            log.warn("Stuck saga detected: {} in state: {}", saga.getSagaId(), saga.getState());
            // Implementation would depend on business rules
            // For now, just log and alert
        }
    }
}

