package com.ecommerce.order_service.outbox;

import com.ecommerce.order_service.domain.entity.OutboxEvent;
import com.ecommerce.order_service.domain.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing transactional outbox events.
 * Ensures atomicity between database changes and event publishing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Save an event to the outbox table.
     * This method should be called within the same transaction as the domain operation.
     */
    @Transactional
    public void saveEvent(String aggregateType, String aggregateId, String eventType, Object eventPayload) {
        try {
            String payload = objectMapper.writeValueAsString(eventPayload);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payload)
                    .published(false)
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.debug("Saved outbox event: {} for aggregate: {}/{}", eventType, aggregateType, aggregateId);
        } catch (JsonProcessingException e) {
            log.error("Error serializing event payload for {}/{}", aggregateType, aggregateId, e);
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }

    /**
     * Get unpublished events for processing.
     */
    @Transactional(readOnly = true)
    public List<OutboxEvent> getUnpublishedEvents() {
        return outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
    }

    /**
     * Mark an event as published.
     */
    @Transactional
    public void markAsPublished(Long eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Outbox event not found: " + eventId));
        event.setPublished(true);
        event.setPublishedAt(LocalDateTime.now());
        outboxEventRepository.save(event);
        log.debug("Marked outbox event {} as published", eventId);
    }

    /**
     * Clean up old published events (for maintenance).
     */
    @Transactional
    public void cleanupOldEvents(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        List<OutboxEvent> oldEvents = outboxEventRepository.findByPublishedTrueAndCreatedAtBefore(cutoffDate);
        outboxEventRepository.deleteAll(oldEvents);
        log.info("Cleaned up {} old outbox events", oldEvents.size());
    }
}

