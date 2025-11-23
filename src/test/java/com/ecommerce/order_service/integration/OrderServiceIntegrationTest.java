package com.ecommerce.order_service.integration;

import com.ecommerce.order_service.api.dto.CreateOrderRequest;
import com.ecommerce.order_service.api.dto.OrderItemRequest;
import com.ecommerce.order_service.api.dto.OrderResponse;
import com.ecommerce.order_service.domain.entity.OutboxEvent;
import com.ecommerce.order_service.domain.entity.Order;
import com.ecommerce.order_service.domain.entity.OrderStatus;
import com.ecommerce.order_service.domain.entity.OrderSaga;
import com.ecommerce.order_service.domain.repository.OutboxEventRepository;
import com.ecommerce.order_service.domain.repository.OrderRepository;
import com.ecommerce.order_service.domain.repository.OrderSagaRepository;
import com.ecommerce.order_service.kafka.event.PaymentSucceededEvent;
import com.ecommerce.order_service.outbox.OutboxPublisher;
import com.ecommerce.order_service.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@Testcontainers
class OrderServiceIntegrationTest {

    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.33")
            .withDatabaseName("order_service_db")
            .withUsername("root")
            .withPassword("root");

    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        mysql.start();
        kafka.start();

        String jdbcUrl = mysql.getJdbcUrl();
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Ensure flyway runs against container DB
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderSagaRepository sagaRepository;

    @Test
    void testCreateOrderAndPaymentSuccessFlow() throws Exception {
        // Arrange: create order request
        CreateOrderRequest req = new CreateOrderRequest();
        req.setUserId("test-user-1");
        req.setShippingAddress("123 Test St");
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId("prod-1");
        item.setProductName("Product 1");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("10.00"));
        req.setItems(List.of(item));

        String correlationId = UUID.randomUUID().toString();

        // Act: create order
        OrderResponse resp = orderService.createOrder(req, null, correlationId);
        Assertions.assertNotNull(resp.getOrderId());
        Assertions.assertNotNull(resp.getSagaId());

        // Ensure outbox has OrderCreated event
        List<OutboxEvent> events = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        boolean hasOrderCreated = events.stream().anyMatch(e -> "OrderCreated".equals(e.getEventType())
                && resp.getOrderId().equals(e.getAggregateId()));
        Assertions.assertTrue(hasOrderCreated, "Outbox should contain OrderCreated event");

        // Publish outbox events (simulate outbox publisher)
        outboxPublisher.publishEvents();

        // Simulate payment service by publishing PaymentSucceeded event to payments.events topic
        PaymentSucceededEvent paymentEvent = PaymentSucceededEvent.builder()
                .orderId(resp.getOrderId())
                .userId(resp.getUserId())
                .paymentId(UUID.randomUUID().toString())
                .sagaId(resp.getSagaId())
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build();

        String payload = objectMapper.writeValueAsString(paymentEvent);
        MessageBuilder<String> msg = MessageBuilder.withPayload(payload)
                .setHeader("eventType", "PaymentSucceeded")
                .setHeader(org.springframework.kafka.support.KafkaHeaders.TOPIC, "payments.events")
                .setHeader(org.springframework.kafka.support.KafkaHeaders.KEY, resp.getOrderId());

        kafkaTemplate.send(msg.build()).get(); // wait for send

        // Wait for consumer to process and orchestrator to update order
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until(() -> {
            Order order = orderRepository.findById(resp.getOrderId()).orElse(null);
            return order != null && OrderStatus.CONFIRMED.equals(order.getStatus());
        });

        // Verify saga state is COMPLETED
        OrderSaga saga = sagaRepository.findById(resp.getSagaId()).orElse(null);
        Assertions.assertNotNull(saga, "Saga should exist");
        Assertions.assertEquals("COMPLETED", saga.getState().name());
    }

    @AfterAll
    static void teardown() {
        try {
            kafka.stop();
        } catch (Exception e) {
            // log and ignore
            System.err.println("Failed to stop kafka container: " + e.getMessage());
        }
        try {
            mysql.stop();
        } catch (Exception e) {
            System.err.println("Failed to stop mysql container: " + e.getMessage());
        }
    }
}
