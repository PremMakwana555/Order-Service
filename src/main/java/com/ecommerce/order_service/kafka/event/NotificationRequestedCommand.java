package com.ecommerce.order_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Command to request a notification to be sent to the user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequestedCommand {

    private String orderId;
    private String userId;
    private String notificationType;
    private String message;
    private String correlationId;
    private String sagaId;
    private LocalDateTime timestamp;
}
