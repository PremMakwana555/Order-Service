package com.ecommerce.order_service.api.controller;

import com.ecommerce.order_service.api.dto.CreateOrderRequest;
import com.ecommerce.order_service.api.dto.OrderItemRequest;
import com.ecommerce.order_service.api.dto.OrderResponse;
import com.ecommerce.order_service.domain.entity.OrderStatus;
import com.ecommerce.order_service.saga.OrderSagaOrchestrator;
import com.ecommerce.order_service.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for OrderController.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderSagaOrchestrator sagaOrchestrator;

    @Test
    void createOrder_shouldReturnCreated() throws Exception {
        // Given
        OrderItemRequest item = OrderItemRequest.builder()
                .productId("product-1")
                .productName("Test Product")
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId("user-123")
                .items(List.of(item))
                .shippingAddress("123 Test Street")
                .build();

        OrderResponse response = OrderResponse.builder()
                .orderId("order-123")
                .userId("user-123")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00"))
                .build();

        when(orderService.createOrder(any(CreateOrderRequest.class), anyString(), anyString()))
                .thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "test-key-123")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value("order-123"));
    }

    @Test
    void createOrder_shouldReturnBadRequestWhenValidationFails() throws Exception {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId("")
                .items(List.of())
                .shippingAddress("")
                .build();

        // When/Then
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrder_shouldReturnOrder() throws Exception {
        // Given
        OrderResponse response = OrderResponse.builder()
                .orderId("order-123")
                .userId("user-123")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00"))
                .build();

        when(orderService.getOrderById("order-123")).thenReturn(response);

        // When/Then
        mockMvc.perform(get("/api/v1/orders/order-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value("order-123"));
    }
}

