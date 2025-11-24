package com.ecommerce.order_service.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * OrderSaga entity for managing saga orchestration state.
 */
@Entity
@Table(name = "order_saga")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSaga {

    @Id
    @Column(name = "saga_id", length = 36)
    private String sagaId;

    @Column(name = "order_id", nullable = false, length = 20)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 50)
    private SagaState state;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
