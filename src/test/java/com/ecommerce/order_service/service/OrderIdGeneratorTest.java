package com.ecommerce.order_service.service;

import com.ecommerce.order_service.domain.entity.Order;
import com.ecommerce.order_service.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderIdGenerator.
 */
@ExtendWith(MockitoExtension.class)
class OrderIdGeneratorTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderIdGenerator orderIdGenerator;

    @Test
    void generateOrderId_shouldReturnValidFormat() {
        // Given
        when(orderRepository.findByOrderId(anyString())).thenReturn(Optional.empty());

        // When
        String orderId = orderIdGenerator.generateOrderId();

        // Then
        assertThat(orderId).isNotNull();
        assertThat(orderId).startsWith("ORD-");
        assertThat(orderId).hasSize(14); // ORD- (4) + 10 digits
        assertThat(orderId.substring(4)).matches("\\d{10}"); // Verify 10 digits
        verify(orderRepository).findByOrderId(orderId);
    }

    @Test
    void generateOrderId_shouldRetryOnCollision() {
        // Given - first call returns collision, second call returns empty
        when(orderRepository.findByOrderId(anyString()))
                .thenReturn(Optional.of(new Order())) // First attempt: collision
                .thenReturn(Optional.empty()); // Second attempt: no collision

        // When
        String orderId = orderIdGenerator.generateOrderId();

        // Then
        assertThat(orderId).isNotNull();
        assertThat(orderId).startsWith("ORD-");
        assertThat(orderId).hasSize(14);
        verify(orderRepository, times(2)).findByOrderId(anyString());
    }

    @Test
    void generateOrderId_shouldThrowExceptionAfterMaxRetries() {
        // Given
        when(orderRepository.findByOrderId(anyString()))
                .thenReturn(Optional.of(new Order())); // Always collision

        // When/Then
        assertThatThrownBy(() -> orderIdGenerator.generateOrderId())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate unique order ID");
        verify(orderRepository, times(10)).findByOrderId(anyString());
    }

    @Test
    void generateOrderId_shouldGenerateUniqueIds() {
        // Given
        when(orderRepository.findByOrderId(anyString())).thenReturn(Optional.empty());

        // When
        String orderId1 = orderIdGenerator.generateOrderId();
        String orderId2 = orderIdGenerator.generateOrderId();
        String orderId3 = orderIdGenerator.generateOrderId();

        // Then
        assertThat(orderId1).isNotEqualTo(orderId2);
        assertThat(orderId2).isNotEqualTo(orderId3);
        assertThat(orderId1).isNotEqualTo(orderId3);
    }
}
