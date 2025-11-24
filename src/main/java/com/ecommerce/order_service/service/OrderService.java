package com.ecommerce.order_service.service;

import com.ecommerce.order_service.api.dto.CreateOrderRequest;
import com.ecommerce.order_service.api.dto.OrderResponse;
import com.ecommerce.order_service.api.mapper.OrderMapper;
import com.ecommerce.order_service.domain.entity.*;
import com.ecommerce.order_service.domain.repository.IdempotencyKeyRepository;
import com.ecommerce.order_service.domain.repository.OrderRepository;
import com.ecommerce.order_service.domain.repository.OrderSagaRepository;
import com.ecommerce.order_service.kafka.event.OrderCreatedEvent;
import com.ecommerce.order_service.kafka.event.OrderLineEvent;
import com.ecommerce.order_service.outbox.OutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for order management operations.
 * Implements saga orchestration pattern with transactional outbox.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderSagaRepository sagaRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxService outboxService;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;
    private final OrderIdGenerator orderIdGenerator;

    /**
     * Create a new order with idempotency support.
     * Implements transactional outbox pattern for atomicity.
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey, String correlationId) {
        log.info("Creating order for user: {} with correlationId: {}", request.getUserId(), correlationId);

        // Check idempotency
        if (idempotencyKey != null) {
            IdempotencyKey existingKey = idempotencyKeyRepository.findById(idempotencyKey).orElse(null);
            if (existingKey != null && existingKey.getExpiresAt().isAfter(LocalDateTime.now())) {
                log.info("Duplicate request detected with idempotency key: {}", idempotencyKey);
                try {
                    return objectMapper.readValue(existingKey.getResponsePayload(), OrderResponse.class);
                } catch (JsonProcessingException e) {
                    log.error("Error deserializing cached response", e);
                }
            }
        }

        // Generate IDs
        String orderId = orderIdGenerator.generateOrderId();
        String sagaId = UUID.randomUUID().toString();

        // Calculate total amount
        BigDecimal totalAmount = request.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Create order entity
        Order order = Order.builder()
                .orderId(orderId)
                .userId(request.getUserId())
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .shippingAddress(request.getShippingAddress())
                .build();

        // Add order lines
        final Order finalOrder = order;
        List<OrderLine> orderLines = orderMapper.toOrderLineList(request.getItems());
        orderLines.forEach(finalOrder::addOrderLine);

        // Save order
        order = orderRepository.save(order);

        // Create saga
        OrderSaga saga = createSaga(orderId, sagaId, SagaState.STARTED, order);
        sagaRepository.save(saga);

        // Publish OrderCreated event via outbox
        publishOrderCreatedEvent(order, correlationId, sagaId);

        // Prepare response
        OrderResponse response = orderMapper.toResponse(order);
        // ensure caller has the sagaId so orchestrator can find the persisted saga
        response.setSagaId(sagaId);

        // Store idempotency key
        if (idempotencyKey != null) {
            storeIdempotencyKey(idempotencyKey, response);
        }

        log.info("Order created successfully: {} with sagaId: {}", orderId, sagaId);
        return response;
    }

    /**
     * Get order by ID.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(String orderId) {
        log.debug("Fetching order: {}", orderId);
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return orderMapper.toResponse(order);
    }

    /**
     * Get all orders for a user.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUserId(String userId) {
        log.debug("Fetching orders for user: {}", userId);
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(orderMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update order status.
     */
    @Transactional
    public void updateOrderStatus(String orderId, OrderStatus status) {
        log.info("Updating order {} to status: {}", orderId, status);
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        order.setStatus(status);
        orderRepository.save(order);
    }

    /**
     * Update order with payment ID.
     */
    @Transactional
    public void updateOrderPaymentId(String orderId, String paymentId) {
        log.info("Updating order {} with payment ID: {}", orderId, paymentId);
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        order.setPaymentId(paymentId);
        orderRepository.save(order);
    }

    private void publishOrderCreatedEvent(Order order, String correlationId, String sagaId) {
        List<OrderLineEvent> lineEvents = order.getOrderLines().stream()
                .map(line -> OrderLineEvent.builder()
                        .productId(line.getProductId())
                        .productName(line.getProductName())
                        .quantity(line.getQuantity())
                        .unitPrice(line.getUnitPrice())
                        .build())
                .collect(Collectors.toList());

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .orderLines(lineEvents)
                .correlationId(correlationId)
                .sagaId(sagaId)
                .timestamp(LocalDateTime.now())
                .build();

        outboxService.saveEvent("Order", order.getOrderId(), "OrderCreated", event);
    }

    private OrderSaga createSaga(String orderId, String sagaId, SagaState state, Order order) {
        try {
            String payload = objectMapper.writeValueAsString(order);
            return OrderSaga.builder()
                    .sagaId(sagaId)
                    .orderId(orderId)
                    .state(state)
                    .payload(payload)
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing saga payload", e);
        }
    }

    private void storeIdempotencyKey(String key, OrderResponse response) {
        try {
            IdempotencyKey idempotencyKey = IdempotencyKey.builder()
                    .idempotencyKey(key)
                    .responsePayload(objectMapper.writeValueAsString(response))
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .build();
            idempotencyKeyRepository.save(idempotencyKey);
        } catch (JsonProcessingException e) {
            log.error("Error storing idempotency key", e);
        } catch (DataIntegrityViolationException dive) {
            // Another request inserted the same idempotency key concurrently. Safe to ignore.
            log.warn("Idempotency key {} already exists (concurrent insert). Reading stored response.", key);
            try {
                IdempotencyKey existing = idempotencyKeyRepository.findById(key).orElse(null);
                if (existing != null) {
                    // No further action; response already stored by concurrent request
                }
            } catch (Exception ex) {
                log.error("Error reading idempotency key after concurrent insert: {}", key, ex);
            }
        }
    }

    /**
     * Custom exception for order not found scenarios.
     */
    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String message) {
            super(message);
        }
    }
}
