package com.ecommerce.order_service.domain.repository;

import com.ecommerce.order_service.domain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for OutboxEvent entity operations.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Find all unpublished events ordered by creation time.
     */
    List<OutboxEvent> findByPublishedFalseOrderByCreatedAtAsc();

    /**
     * Find unpublished events for a specific aggregate.
     */
    List<OutboxEvent> findByAggregateIdAndPublishedFalse(String aggregateId);

    /**
     * Find events created before a specific time (for cleanup).
     */
    List<OutboxEvent> findByPublishedTrueAndCreatedAtBefore(LocalDateTime before);
}

