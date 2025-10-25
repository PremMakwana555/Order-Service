# Order Service - E-commerce Microservice

A comprehensive order management microservice built with Spring Boot 3.x, implementing saga orchestration pattern with transactional outbox for reliable event-driven architecture.

## Features

- ✅ **RESTful API** for order management (create, retrieve orders)
- ✅ **Saga Orchestration** for coordinating order and payment workflows
- ✅ **Transactional Outbox Pattern** for guaranteed event publishing
- ✅ **Idempotency Support** via Idempotency-Key header
- ✅ **Event-Driven Architecture** using Apache Kafka
- ✅ **Optimistic Locking** for concurrent order updates
- ✅ **Database Migrations** with Flyway
- ✅ **Resilience4j** for circuit breakers and retries
- ✅ **Redis Caching** for idempotency keys
- ✅ **Comprehensive Logging** with correlation IDs and saga IDs
- ✅ **MapStruct** for DTO-Entity mapping
- ✅ **Health Checks** and metrics via Spring Actuator

## Architecture

### Key Patterns Implemented

1. **Saga Orchestration**: Order service acts as orchestrator, coordinating payment workflow
2. **Transactional Outbox**: Ensures atomicity between database changes and event publishing
3. **Idempotency**: Prevents duplicate order creation using idempotency keys
4. **Event Sourcing**: All state changes published as domain events

### Technology Stack

- **Java 21**
- **Spring Boot 3.3.5**
- **MySQL 8.0** - Primary database
- **Apache Kafka** - Message broker
- **Redis** - Caching and idempotency
- **Flyway** - Database migrations
- **MapStruct** - Object mapping
- **Resilience4j** - Resilience patterns
- **Lombok** - Boilerplate reduction
- **JUnit & Mockito** - Testing

## Getting Started

### Prerequisites

- Java 21 or higher
- Docker and Docker Compose
- Maven 3.8+

### Running Locally

1. **Start infrastructure services**:
```bash
docker-compose up -d
```

This starts:
- MySQL on port 3306
- Kafka on port 9092
- Zookeeper on port 2181
- Redis on port 6379

2. **Build the application**:
```bash
./mvnw clean install
```

3. **Run the application**:
```bash
./mvnw spring-boot:run
```

The service will be available at `http://localhost:8080`

### Database Migrations

Flyway migrations run automatically on startup. Tables created:
- `orders` - Main order data with optimistic locking
- `order_lines` - Order line items
- `outbox_events` - Transactional outbox for events
- `order_saga` - Saga orchestration state
- `idempotency_keys` - Idempotency tracking

## API Endpoints

### Create Order
```bash
POST /api/v1/orders
Headers:
  Content-Type: application/json
  Idempotency-Key: <unique-key>
  X-Correlation-Id: <correlation-id>

Body:
{
  "userId": "user-123",
  "shippingAddress": "123 Main St, City, Country",
  "items": [
    {
      "productId": "product-456",
      "productName": "Sample Product",
      "quantity": 2,
      "unitPrice": 29.99
    }
  ]
}
```

### Get Order
```bash
GET /api/v1/orders/{orderId}
```

### Get User Orders
```bash
GET /api/v1/orders/user/{userId}
```

## Kafka Topics

- `orders.events` - Order lifecycle events (OrderCreated, OrderConfirmed, OrderCancelled)
- `orders.commands` - Commands for order operations
- `payments.commands` - Payment request commands
- `payments.events` - Payment result events (PaymentSucceeded, PaymentFailed)
- `notifications.commands` - Notification requests

## Saga Flow

1. **Order Created** → Order saved with status `PENDING`
2. **Payment Requested** → PaymentRequestCommand published
3. **Payment Processing** → Payment service processes payment
4. **Success Path**:
   - PaymentSucceededEvent received
   - Order status → `CONFIRMED`
   - OrderConfirmedEvent published
5. **Failure Path**:
   - PaymentFailedEvent received
   - Order status → `CANCELLED` (compensation)
   - OrderCancelledEvent published

## Event Headers

All events include:
- `correlationId` - Trace requests across services
- `sagaId` - Track saga instances
- `causationId` - Event causation chain
- `timestamp` - Event timestamp
- `source` - Source service identifier

## Monitoring

### Health Check
```bash
GET /actuator/health
```

### Metrics
```bash
GET /actuator/metrics
GET /actuator/prometheus
```

## Testing

Run unit tests:
```bash
./mvnw test
```

Run integration tests with Testcontainers:
```bash
./mvnw verify
```

## Configuration

Key configuration in `application.properties`:

- **Database**: MySQL connection settings
- **Kafka**: Bootstrap servers and consumer/producer configs
- **Redis**: Connection settings
- **Resilience4j**: Circuit breaker and retry policies
- **Logging**: MDC context with correlationId and sagaId

## Project Structure

```
src/main/java/com/ecommerce/order_service/
├── api/
│   ├── controller/     # REST controllers
│   ├── dto/            # Data Transfer Objects
│   └── mapper/         # MapStruct mappers
├── domain/
│   ├── entity/         # JPA entities
│   └── repository/     # Spring Data repositories
├── service/            # Business logic layer
├── saga/               # Saga orchestration
├── outbox/             # Transactional outbox pattern
├── kafka/
│   ├── event/          # Event models
│   └── consumer/       # Kafka consumers
└── config/             # Spring configurations
```

## Development Guidelines

### Idempotency
Always include `Idempotency-Key` header for POST requests to prevent duplicate orders.

### Correlation IDs
Use `X-Correlation-Id` header to trace requests across services. Auto-generated if not provided.

### Error Handling
- Validation errors return 400 with field-level details
- Not found errors return 404
- Server errors return 500 with correlation ID for tracing

## Maintenance Tasks

### Outbox Event Cleanup
Runs daily at 2 AM, removes events older than 7 days.

### Stuck Saga Recovery
Implement monitoring for sagas not updated in 30+ minutes.

## Contributing

Follow the coding guidelines from `.github/copilot-instructions.md`:
- Keep methods small and focused
- Use DTOs for API boundaries
- Add Javadoc for public methods
- Include correlation/saga IDs in logs
- Write tests for new features

## License

See LICENSE file for details.

---

Built following the PRD specifications with saga orchestration, transactional outbox, and event-driven architecture patterns.

