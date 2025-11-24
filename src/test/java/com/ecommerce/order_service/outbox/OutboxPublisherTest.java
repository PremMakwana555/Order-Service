package com.ecommerce.order_service.outbox;

import com.ecommerce.order_service.domain.entity.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    void publishEvents_shouldPublishAndMarkAsPublished() throws Exception {
        // Given
        OutboxEvent event = new OutboxEvent();
        event.setId(1L);
        event.setEventType("OrderCreated");
        event.setAggregateType("Order");
        event.setAggregateId("order-1");
        event.setPayload("{}");

        when(outboxService.getUnpublishedEvents()).thenReturn(List.of(event));
        when(objectMapper.readValue(anyString(), eq(Object.class))).thenReturn(new Object());

        // When
        outboxPublisher.publishEvents();

        // Then
        verify(kafkaTemplate).send(any(org.springframework.messaging.Message.class));
        verify(outboxService).markAsPublished(1L);
    }

    @Test
    void publishEvents_shouldNotMarkAsPublishedOnFailure() throws Exception {
        // Given
        OutboxEvent event = new OutboxEvent();
        event.setId(1L);
        event.setEventType("OrderCreated");

        when(outboxService.getUnpublishedEvents()).thenReturn(List.of(event));
        when(objectMapper.readValue(anyString(), eq(Object.class)))
                .thenThrow(new RuntimeException("Serialization error"));

        // When
        outboxPublisher.publishEvents();

        // Then
        verify(kafkaTemplate, never()).send(any(org.springframework.messaging.Message.class));
        verify(outboxService, never()).markAsPublished(anyLong());
    }
}
