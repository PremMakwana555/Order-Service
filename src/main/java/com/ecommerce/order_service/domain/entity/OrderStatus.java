package com.ecommerce.order_service.domain.entity;

/**
 * Order status enumeration representing the lifecycle of an order.
 */
public enum OrderStatus {
    PENDING,
    PAYMENT_REQUESTED,
    CONFIRMED,
    CANCELLED,
    FAILED,
    SHIPPED,
    DELIVERED
}

