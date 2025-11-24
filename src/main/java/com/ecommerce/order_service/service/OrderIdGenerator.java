package com.ecommerce.order_service.service;

import com.ecommerce.order_service.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Service to generate unique human-readable order IDs.
 * Format: ORD-XXXXXXXXXX (10-digit random number)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderIdGenerator {

    private final OrderRepository orderRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String ORDER_ID_PREFIX = "ORD-";
    private static final int MAX_RETRIES = 10;

    /**
     * Generate a unique order ID with format ORD-XXXXXXXXXX.
     * Uses SecureRandom for cryptographically strong random numbers.
     * Checks database for uniqueness and retries on collision.
     *
     * @return unique order ID
     * @throws IllegalStateException if unable to generate unique ID after
     *                               MAX_RETRIES
     */
    public String generateOrderId() {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String orderId = generateRandomOrderId();

            // Check if this orderId already exists
            if (!orderRepository.findByOrderId(orderId).isPresent()) {
                log.debug("Generated unique order ID: {} in {} attempts", orderId, attempt + 1);
                return orderId;
            }

            log.warn("Order ID collision detected: {}, retrying... (attempt {}/{})",
                    orderId, attempt + 1, MAX_RETRIES);
        }

        throw new IllegalStateException("Failed to generate unique order ID after " + MAX_RETRIES + " attempts");
    }

    /**
     * Generate a random order ID without uniqueness check.
     * Format: ORD-XXXXXXXXXX
     *
     * @return random order ID
     */
    private String generateRandomOrderId() {
        // Generate a 10-digit number (1,000,000,000 to 9,999,999,999)
        long randomNumber = 1_000_000_000L + (Math.abs(secureRandom.nextLong()) % 9_000_000_000L);
        return ORDER_ID_PREFIX + randomNumber;
    }
}
