package com.ecommerce.order_service.domain.entity;

/**
 * Saga state enumeration for tracking saga orchestration progress.
 */
public enum SagaState {
    STARTED,
    PAYMENT_REQUESTED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}

