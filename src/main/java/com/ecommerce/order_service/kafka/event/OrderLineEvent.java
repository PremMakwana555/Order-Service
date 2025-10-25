package com.ecommerce.order_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Event model for order line items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLineEvent {

    private String productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
}

