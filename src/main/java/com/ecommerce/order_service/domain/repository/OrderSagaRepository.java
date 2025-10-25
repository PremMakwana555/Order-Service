package com.ecommerce.order_service.domain.repository;

import com.ecommerce.order_service.domain.entity.OrderSaga;
import com.ecommerce.order_service.domain.entity.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for OrderSaga entity operations.
 */
@Repository
public interface OrderSagaRepository extends JpaRepository<OrderSaga, String> {

    /**
     * Find saga by order ID.
     */
    Optional<OrderSaga> findByOrderId(String orderId);

    /**
     * Find sagas by state.
     */
    List<OrderSaga> findByState(SagaState state);

    /**
     * Find stuck sagas (not updated for a while).
     */
    List<OrderSaga> findByStateNotInAndLastUpdatedBefore(List<SagaState> excludedStates, LocalDateTime before);
}

