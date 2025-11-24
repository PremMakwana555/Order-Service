//package com.ecommerce.order_service;
//
//import com.ecommerce.order_service.api.dto.CreateOrderRequest;
//import com.ecommerce.order_service.api.dto.OrderItemRequest;
//import com.ecommerce.order_service.domain.entity.Order;
//import com.ecommerce.order_service.domain.entity.OrderSaga;
//import com.ecommerce.order_service.domain.entity.OrderStatus;
//import com.ecommerce.order_service.domain.entity.SagaState;
//import com.ecommerce.order_service.domain.repository.OrderRepository;
//import com.ecommerce.order_service.domain.repository.OrderSagaRepository;
//import com.ecommerce.order_service.kafka.event.PaymentSucceededEvent;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.http.MediaType;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.springframework.test.web.servlet.MockMvc;
//import org.testcontainers.containers.KafkaContainer;
//import org.testcontainers.containers.MySQLContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import org.testcontainers.utility.DockerImageName;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.UUID;
//import java.util.concurrent.TimeUnit;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.awaitility.Awaitility.await;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//import com.ecommerce.order_service.api.mapper.OrderMapperImpl;
//import org.springframework.context.annotation.Import;
//
//@SpringBootTest
//@Testcontainers
//@AutoConfigureMockMvc
//@Import(OrderMapperImpl.class)
//class OrderIntegrationTest {
//
//    @Container
//    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");
//
//    @Container
//    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @Autowired
//    private OrderRepository orderRepository;
//
//    @Autowired
//    private OrderSagaRepository sagaRepository;
//
//    @Autowired
//    private KafkaTemplate<String, Object> kafkaTemplate;
//
//    @DynamicPropertySource
//    static void configureProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.datasource.url", mysql::getJdbcUrl);
//        registry.add("spring.datasource.username", mysql::getUsername);
//        registry.add("spring.datasource.password", mysql::getPassword);
//        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
//    }
//
//    @Test
//    void createOrder_shouldStartSagaAndProcessPayment() throws Exception {
//        // 1. Create Order
//        OrderItemRequest item = OrderItemRequest.builder()
//                .productId("prod-1")
//                .productName("Laptop")
//                .quantity(1)
//                .unitPrice(new BigDecimal("1000.00"))
//                .build();
//
//        CreateOrderRequest request = CreateOrderRequest.builder()
//                .userId("user-1")
//                .items(List.of(item))
//                .shippingAddress("123 Main St")
//                .build();
//
//        mockMvc.perform(post("/api/v1/orders")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isCreated());
//
//        // Wait for order to be persisted
//        await().atMost(10, TimeUnit.SECONDS).until(() -> orderRepository.count() > 0);
//
//        Order order = orderRepository.findAll().get(0);
//        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
//
//        // Wait for Saga to be created
//        await().atMost(10, TimeUnit.SECONDS).until(() -> sagaRepository.count() > 0);
//        OrderSaga saga = sagaRepository.findAll().get(0);
//        assertThat(saga.getOrderId()).isEqualTo(order.getOrderId());
//
//        // 2. Simulate Payment Success Event
//        PaymentSucceededEvent paymentEvent = PaymentSucceededEvent.builder()
//                .sagaId(saga.getSagaId())
//                .orderId(order.getOrderId())
//                .userId("user-1")
//                .paymentId("pay-" + UUID.randomUUID())
//                .correlationId(UUID.randomUUID().toString())
//                .timestamp(LocalDateTime.now())
//                .build();
//
//        // Send to payment events topic
//        kafkaTemplate.send("payments.events", paymentEvent);
//
//        // 3. Verify Order Confirmed
//        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
//            Order updatedOrder = orderRepository.findById(order.getOrderId()).orElseThrow();
//            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
//            assertThat(updatedOrder.getPaymentId()).isNotNull();
//        });
//
//        // 4. Verify Saga Completed
//        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
//            OrderSaga updatedSaga = sagaRepository.findById(saga.getSagaId()).orElseThrow();
//            assertThat(updatedSaga.getState()).isEqualTo(SagaState.COMPLETED);
//        });
//    }
//
//    @Test
//    void createOrder_shouldCompensate_whenPaymentFails() throws Exception {
//        // 1. Create Order
//        OrderItemRequest item = OrderItemRequest.builder()
//                .productId("prod-2")
//                .productName("Phone")
//                .quantity(1)
//                .unitPrice(new BigDecimal("500.00"))
//                .build();
//
//        CreateOrderRequest request = CreateOrderRequest.builder()
//                .userId("user-2")
//                .items(List.of(item))
//                .shippingAddress("456 Test St")
//                .build();
//
//        mockMvc.perform(post("/api/v1/orders")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isCreated());
//
//        // Wait for order to be persisted
//        await().atMost(10, TimeUnit.SECONDS).until(() -> orderRepository.count() > 1);
//
//        // Find the latest order (assuming ID generation is sequential or we filter by
//        // user)
//        Order order = orderRepository.findByUserIdOrderByCreatedAtDesc("user-2").get(0);
//        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
//
//        // Wait for Saga
//        await().atMost(10, TimeUnit.SECONDS).until(() -> sagaRepository.findByOrderId(order.getOrderId()).isPresent());
//        OrderSaga saga = sagaRepository.findByOrderId(order.getOrderId()).get();
//
//        // 2. Simulate Payment Failure Event
//        com.ecommerce.order_service.kafka.event.PaymentFailedEvent paymentEvent = com.ecommerce.order_service.kafka.event.PaymentFailedEvent
//                .builder()
//                .sagaId(saga.getSagaId())
//                .orderId(order.getOrderId())
//                .reason("Insufficient Funds")
//                .correlationId(UUID.randomUUID().toString())
//                .timestamp(LocalDateTime.now())
//                .build();
//
//        // Send to payment events topic
//        kafkaTemplate.send("payments.events", paymentEvent);
//
//        // 3. Verify Order Cancelled
//        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
//            Order updatedOrder = orderRepository.findById(order.getOrderId()).orElseThrow();
//            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
//        });
//
//        // 4. Verify Saga Compensated
//        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
//            OrderSaga updatedSaga = sagaRepository.findById(saga.getSagaId()).orElseThrow();
//            assertThat(updatedSaga.getState()).isEqualTo(SagaState.COMPENSATED);
//        });
//    }
//}
