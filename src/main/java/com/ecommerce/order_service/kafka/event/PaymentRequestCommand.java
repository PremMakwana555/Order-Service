package com.ecommerce.order_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Command to request payment processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestCommand {

    private String orderId;
    private String userId;
    private BigDecimal amount;
    private String correlationId;
    private String sagaId;
    private LocalDateTime timestamp;
}

