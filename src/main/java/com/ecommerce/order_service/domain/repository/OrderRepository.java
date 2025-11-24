package com.ecommerce.order_service.domain.repository;

import com.ecommerce.order_service.domain.entity.Order;
import com.ecommerce.order_service.domain.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Order entity operations.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Find order by business order ID.
     */
    Optional<Order> findByOrderId(String orderId);

    /**
     * Find all orders for a specific user.
     */
    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find orders by status.
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * Find orders by user and status.
     */
    List<Order> findByUserIdAndStatus(String userId, OrderStatus status);

    /**
     * Find order by payment ID.
     */
    Optional<Order> findByPaymentId(String paymentId);

    /**
     * Find orders created after a specific date.
     */
    List<Order> findByCreatedAtAfter(LocalDateTime date);
}
