package com.ecommerce.order_service.domain.repository;

import com.ecommerce.order_service.domain.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for IdempotencyKey entity operations.
 */
@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    /**
     * Find expired idempotency keys for cleanup.
     */
    List<IdempotencyKey> findByExpiresAtBefore(LocalDateTime now);
}

