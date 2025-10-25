# Ecommerce Website — PRD & High-Level Design (HLD)

**Purpose:** concise, dev-focused summary of the uploaded PRD/HLD so Copilot and contributors can use it as ground truth for generating code, tests, infra, and docs.

---

## 1 — Executive summary

* **Primary goal:** Build an e-commerce platform with microservices (user, product catalog, cart, orders, payments, notifications) using MySQL, Kafka, Redis, and Elasticsearch as described in the PRD.
* **Key design decisions (from PRD):**

    * Microservices architecture behind an API Gateway.
    * Message broker: **Kafka** for async communication and eventing between services.
    * Use **MySQL** for structured data; **MongoDB** for flexible cart data; **Redis** for caching; **Elasticsearch** for product search.
    * Order Management integrates with Payment via Kafka (payment service produces payment-confirmation events).

---

## 2 — Functional requirements (condensed)

1. **User Management**

    * Register, login (secure), profile edit, password reset.
2. **Product Catalog**

    * Browse by categories, product detail pages, search with Elasticsearch.
3. **Cart & Checkout**

    * Add/remove items, view cart, checkout flow with address & payment method.
4. **Order Management**

    * Create orders, persist history, provide order tracking and confirmations.
5. **Payment**

    * Support multiple methods, secure transactions, produce receipts.
6. **Notification**

    * Send emails/SMS for registrations, order updates, payment receipts.

---

## 3 — HLD: Components & responsibilities

* **Load Balancer (LB)** — distribute traffic (e.g., AWS ELB).
* **API Gateway** — central entry point, route to services, handle auth & rate limits (suggested: Kong).
* **Microservices**

    * **User Service** — MySQL; publish user events to Kafka.
    * **Product Catalog** — MySQL + Elasticsearch; publish product events.
    * **Cart Service** — MongoDB (fast flexible model) + Redis caching.
    * **Order Management** — MySQL; coordinates order lifecycle; interacts with Payment via Kafka.
    * **Payment Service** — MySQL; integrates with external payment gateway; publishes `PaymentSucceeded`/`PaymentFailed` to Kafka.
    * **Notification Service** — subscribes to events and sends emails (e.g., Amazon SES).
* **Other infra**

    * **Kafka** — central message broker & event store.
    * **Redis** — caching (and optionally idempotency / session store).
    * **Elasticsearch** — search index for catalog.
    * **Monitoring/Tracing** — Prometheus / Grafana / Jaeger.

---

## 4 — Datastores (mapping)

* **MySQL** — User, Product metadata, Orders, Payments, Saga / Outbox tables.
* **MongoDB** — Cart documents for flexibility.
* **Redis** — Session caching, cart cache, idempotency keys.
* **Elasticsearch** — Product search index.

---

## 5 — Kafka topics (recommended)

* `user.events` — user lifecycle events (e.g., `UserRegistered`)
* `product.events` — product created/updated
* `cart.events` — cart changes (optional)
* `orders.commands` / `orders.events` — order lifecycle (commands & domain events)
* `payments.commands` — payment request commands
* `payments.events` — payment result events (`PaymentSucceeded`, `PaymentFailed`)
* `notifications.commands` — messages to trigger notifications

**Message headers to include:** `correlationId`, `sagaId` (for long-running flows), `causationId`, `timestamp`, `source`.

---

## 6 — Typical flows (short)

### Search & Browse

1. Client → API Gateway → Product Service → Elasticsearch.
2. Product Service returns results.

### Add-to-Cart

1. Client → Cart Service (MongoDB) → publish `cart.events` to Kafka for downstream consumers.

### Checkout → Order → Payment (recommended saga orchestration)

1. Client POST `/api/v1/orders` → Order Service creates order (status `PENDING`) + writes `OrderCreated` to **outbox** in same DB transaction.
2. OutboxPublisher publishes `orders.events` / `payments.commands`.
3. Payment Service consumes `payments.commands`, attempts payment, publishes `payments.events`.
4. Order Service (orchestrator) consumes `payments.events`:

    * On success → mark `CONFIRMED` → publish `OrderConfirmed`.
    * On failure → perform compensation (cancel order / release inventory) → publish `OrderCancelled`.

---

## 7 — Non-functional & operational requirements

* **Security:** secure auth (OAuth2/JWT), do not store raw card data (use tokenized gateway), TLS everywhere.
* **Consistency:** event-driven eventual consistency between services; use transactional outbox for DB→Kafka atomicity.
* **Resilience:** circuit breakers, retries with exponential backoff, dead-letter topics for poison messages.
* **Observability:** structured logs with `correlationId`/`sagaId`, metrics for order throughput/failures, tracing for cross-service requests.
* **Scalability:** Kafka partitions (partition by `orderId`/`sagaId`), scale stateless services horizontally.

---

## 8 — Suggested DB tables (minimal)

* `orders(order_id UUID PK, user_id, status, total_amount, payment_id, created_at, updated_at, version)`
* `order_lines(id, order_id FK, product_id, quantity, unit_price)`
* `outbox_events(id, aggregate_type, aggregate_id, event_type, payload JSON, published boolean, created_at)`
* `order_saga(saga_id, order_id, state, payload JSON, last_updated)`
* `idempotency_keys(key, created_at, response_payload)`

---

## 9 — Dev & Copilot usage notes (how to use this doc)

* **Language / stack:** Java 17 + Spring Boot 3.x (recommended); alternatives: Python (FastAPI/Sanic) for small microservices or simulations.
* **Package layout suggestion:** `api`, `service`, `domain` (entities/repos), `kafka`, `outbox`, `config`, `tests`.
* **Testing:** JUnit + Mockito + Testcontainers (Kafka, MySQL). Add contract tests for Payment integration.
* **Infra for demo:** Use Docker Compose (Zookeeper + Kafka + MySQL + Redis + Elasticsearch) in dev; use managed services (Confluent/DO/Heroku + managed MySQL) in prod.

---

## 10 — Interview-friendly talking points (quick)

* Why Saga orchestration vs choreography and why choose outbox (avoid 2PC).
* How idempotency keys + consumer design yield at-least-once semantics safely.
* How to detect and recover stuck sagas (saga table + reconciler + alerts).
* How to scale Kafka consumers (partition by `orderId`).

---

## 11 — Where this came from

This doc is a concise, developer-focused extraction of the uploaded PRD / HLD: *Ecommerce Website PRD HLD.pdf*.
(Reference source: original PRD file.)

---

---

# .github/copilot-instructions.md (for repository root)

> Place this file at `.github/copilot-instructions.md` in your repo. Copilot Chat and Copilot will use it as per-repo guidance when generating code or suggestions.

## Repo-level Copilot instructions (short & specific)

* **Primary language:** Java 17, Spring Boot 3.x
* **Project style / layout:** Use modular packages: `api`, `service`, `domain`, `kafka`, `outbox`, `config`, `tests`.
* **Patterns required:**

    * Use **Saga orchestration** for long-running workflows (Order service acts as orchestrator by default).
    * Use **Transactional Outbox** to guarantee DB changes + events atomicity.
    * Use **Idempotency-Key** header for POST endpoints (store keys in Redis or DB table `idempotency_keys`).
* **Kafka & messages:**

    * Use JSON or Avro schemas; include headers `correlationId`, `sagaId`, `causationId`, `timestamp`.
    * Topic names: `orders.commands`, `orders.events`, `payments.commands`, `payments.events`.
* **Testing:** Prefer JUnit + Testcontainers for integration tests involving Kafka + MySQL.
* **Error handling & resilience:** Use Resilience4j for retries and circuit breakers around external calls.
* **Security:** Do not generate code that stores or logs raw card data. Use tokenized flows for payment integrations.
* **Coding style:** Keep methods small, use DTOs for API boundaries, use MapStruct for DTO↔Entity mapping if available.
* **Docstrings & comments:** Generate Javadoc for public service methods and brief inline comments for complex saga transitions.

## How to refer to these instructions when prompting Copilot Chat

* Use `@workspace` to have Copilot consider the whole repository.
* Reference specific files with `#docs/PRD_HLD.md` or `#src/...` when asking for code generation.
* Example prompt: `@workspace Using #docs/PRD_HLD.md and .github/copilot-instructions.md as spec, generate a Flyway migration to create orders and outbox tables and an Order entity with optimistic locking.`

---

*End of document.*
