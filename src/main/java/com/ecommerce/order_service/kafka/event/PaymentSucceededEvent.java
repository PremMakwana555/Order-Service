package com.ecommerce.order_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event received when payment succeeds.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSucceededEvent {

    private String paymentId;
    private String orderId;
    private String userId;
    private String correlationId;
    private String sagaId;
    private LocalDateTime timestamp;
}

