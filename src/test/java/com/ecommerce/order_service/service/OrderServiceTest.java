package com.ecommerce.order_service.service;

import com.ecommerce.order_service.api.dto.CreateOrderRequest;
import com.ecommerce.order_service.api.dto.OrderItemRequest;
import com.ecommerce.order_service.api.dto.OrderResponse;
import com.ecommerce.order_service.api.mapper.OrderMapper;
import com.ecommerce.order_service.domain.entity.Order;
import com.ecommerce.order_service.domain.entity.OrderLine;
import com.ecommerce.order_service.domain.entity.OrderStatus;
import com.ecommerce.order_service.domain.repository.IdempotencyKeyRepository;
import com.ecommerce.order_service.domain.repository.OrderRepository;
import com.ecommerce.order_service.domain.repository.OrderSagaRepository;
import com.ecommerce.order_service.outbox.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderSagaRepository sagaRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OrderIdGenerator orderIdGenerator;

    @InjectMocks
    private OrderService orderService;

    private CreateOrderRequest createOrderRequest;
    private Order order;

    @BeforeEach
    void setUp() {
        OrderItemRequest item = OrderItemRequest.builder()
                .productId("product-1")
                .productName("Test Product")
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .build();

        createOrderRequest = CreateOrderRequest.builder()
                .userId("user-123")
                .items(List.of(item))
                .shippingAddress("123 Test Street")
                .build();

        order = Order.builder()
                .orderId("order-123")
                .userId("user-123")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00"))
                .shippingAddress("123 Test Street")
                .build();
    }

    @Test
    void createOrder_shouldCreateOrderSuccessfully() throws Exception {
        // Given
        when(orderIdGenerator.generateOrderId()).thenReturn("ORD-1234567890");
        when(idempotencyKeyRepository.findById(anyString())).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(sagaRepository.save(any())).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // Mock toResponse
        when(orderMapper.toResponse(any(Order.class))).thenReturn(new OrderResponse());
        when(orderMapper.toOrderLineList(anyList())).thenReturn(java.util.List.of(new OrderLine()));
        OrderResponse response = orderService.createOrder(createOrderRequest, "idempotency-key-123", "correlation-123");

        // Then
        assertThat(response).isNotNull();
        verify(orderRepository).save(any(Order.class));
        verify(sagaRepository).save(any());
        verify(outboxService).saveEvent(anyString(), anyString(), anyString(), any());
    }

    @Test
    void getOrderById_shouldReturnOrder() {
        // Given
        when(orderRepository.findByOrderId("order-123")).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(new OrderResponse());

        // When
        OrderResponse response = orderService.getOrderById("order-123");

        // Then
        assertThat(response).isNotNull();
        verify(orderRepository).findByOrderId("order-123");
    }

    @Test
    void getOrderById_shouldThrowExceptionWhenNotFound() {
        // Given
        when(orderRepository.findByOrderId("order-123")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> orderService.getOrderById("order-123"))
                .isInstanceOf(OrderService.OrderNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void updateOrderStatus_shouldUpdateSuccessfully() {
        // Given
        when(orderRepository.findByOrderId("order-123")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        // When
        orderService.updateOrderStatus("order-123", OrderStatus.CONFIRMED);

        // Then
        verify(orderRepository).save(any(Order.class));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
