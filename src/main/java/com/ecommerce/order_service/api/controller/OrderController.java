package com.ecommerce.order_service.api.controller;

import com.ecommerce.order_service.api.dto.ApiResponse;
import com.ecommerce.order_service.api.dto.CreateOrderRequest;
import com.ecommerce.order_service.api.dto.OrderResponse;
import com.ecommerce.order_service.saga.OrderSagaOrchestrator;
import com.ecommerce.order_service.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for order management operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderSagaOrchestrator sagaOrchestrator;

    /**
     * Create a new order.
     * Supports idempotency via Idempotency-Key header.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        // Generate correlation ID if not provided
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);

        try {
            log.info("Creating order for user: {}", request.getUserId());

            OrderResponse orderResponse = orderService.createOrder(request, idempotencyKey, correlationId);

            // Trigger saga to request payment
            sagaOrchestrator.startPaymentRequest(
                    orderResponse.getSagaId(), // use the persisted sagaId returned by the service
                    orderResponse.getOrderId(),
                    orderResponse.getUserId(),
                    orderResponse.getTotalAmount(),
                    correlationId
            );

            ApiResponse<OrderResponse> response = ApiResponse.success(
                    orderResponse,
                    "Order created successfully",
                    correlationId
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Get order by ID.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @PathVariable String orderId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);

        try {
            log.info("Fetching order: {}", orderId);
            OrderResponse orderResponse = orderService.getOrderById(orderId);

            ApiResponse<OrderResponse> response = ApiResponse.success(
                    orderResponse,
                    "Order retrieved successfully",
                    correlationId
            );

            return ResponseEntity.ok(response);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Get all orders for a user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getUserOrders(
            @PathVariable String userId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);

        try {
            log.info("Fetching orders for user: {}", userId);
            List<OrderResponse> orders = orderService.getOrdersByUserId(userId);

            ApiResponse<List<OrderResponse>> response = ApiResponse.success(
                    orders,
                    "Orders retrieved successfully",
                    correlationId
            );

            return ResponseEntity.ok(response);
        } finally {
            MDC.clear();
        }
    }
}
