-- V2: Refactor Order ID - Add surrogate primary key and keep order_id as business key
-- This migration adds a BIGINT AUTO_INCREMENT id as the primary key while keeping order_id as the foreign key reference

-- Step 1: Drop foreign key constraints referencing orders.order_id
ALTER TABLE order_lines DROP FOREIGN KEY order_lines_ibfk_1;
ALTER TABLE order_saga DROP FOREIGN KEY order_saga_ibfk_1;

-- Step 2: Add id column first as AUTO_INCREMENT with temporary key
-- MySQL requires AUTO_INCREMENT column to be a key
ALTER TABLE orders
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT FIRST,
    ADD KEY temp_key (id);

-- Step 3: Drop old primary key and make id the new primary key
ALTER TABLE orders
    DROP PRIMARY KEY,
    DROP KEY temp_key,
    ADD PRIMARY KEY (id),
    ADD UNIQUE KEY uk_order_id (order_id);

-- Step 4: Re-add foreign key constraints on order_lines referencing order_id
ALTER TABLE order_lines
    ADD CONSTRAINT fk_order_lines_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE;

-- Step 5: Re-add foreign key constraints on order_saga referencing order_id
ALTER TABLE order_saga
    ADD CONSTRAINT fk_order_saga_order FOREIGN KEY (order_id) REFERENCES orders(order_id);





-- Test data for Order Service
-- This file is automatically loaded by Spring Boot after schema creation

-- Insert test orders
INSERT INTO orders (order_id, user_id, status, total_amount, payment_id, shipping_address, created_at, updated_at, version)
VALUES
    ('ORD-1234567890', 'user-001', 'PENDING', 299.99, NULL, '123 Main St, New York, NY 10001', NOW(), NOW(), 0),
    ('ORD-2345678901', 'user-002', 'CONFIRMED', 549.50, 'pay-abc123', '456 Oak Ave, Los Angeles, CA 90001', NOW(), NOW(), 0),
    ('ORD-3456789012', 'user-001', 'CANCELLED', 129.99, NULL, '123 Main St, New York, NY 10001', NOW(), NOW(), 0),
    ('ORD-4567890123', 'user-003', 'CONFIRMED', 899.00, 'pay-def456', '789 Pine Rd, Chicago, IL 60601', NOW(), NOW(), 0),
    ('ORD-5678901234', 'user-002', 'PENDING', 175.25, NULL, '456 Oak Ave, Los Angeles, CA 90001', NOW(), NOW(), 0);

-- Insert order lines for order 1
INSERT INTO order_lines (order_id, product_id, product_name, quantity, unit_price)
VALUES
    ('ORD-1234567890', 'prod-001', 'Laptop Stand', 1, 49.99),
    ('ORD-1234567890', 'prod-002', 'Wireless Mouse', 2, 25.00),
    ('ORD-1234567890', 'prod-003', 'USB-C Cable', 4, 50.00);

-- Insert order lines for order 2
INSERT INTO order_lines (order_id, product_id, product_name, quantity, unit_price)
VALUES
    ('ORD-2345678901', 'prod-004', 'Mechanical Keyboard', 1, 149.99),
    ('ORD-2345678901', 'prod-005', 'Monitor 27"', 1, 399.51);

-- Insert order lines for order 3
INSERT INTO order_lines (order_id, product_id, product_name, quantity, unit_price)
VALUES
    ('ORD-3456789012', 'prod-006', 'Webcam HD', 1, 79.99),
    ('ORD-3456789012', 'prod-007', 'Headphones', 1, 50.00);

-- Insert order lines for order 4
INSERT INTO order_lines (order_id, product_id, product_name, quantity, unit_price)
VALUES
    ('ORD-4567890123', 'prod-008', 'Laptop Dell XPS 15', 1, 899.00);

-- Insert order lines for order 5
INSERT INTO order_lines (order_id, product_id, product_name, quantity, unit_price)
VALUES
    ('ORD-5678901234', 'prod-009', 'Phone Case', 3, 15.00),
    ('ORD-5678901234', 'prod-010', 'Screen Protector', 3, 10.00),
    ('ORD-5678901234', 'prod-011', 'Charging Cable', 5, 20.05);

-- Insert saga records for orders
INSERT INTO order_saga (saga_id, order_id, state, payload, last_updated, created_at)
VALUES
    ('saga-001' , 'ORD-1234567890', 'PAYMENT_PENDING', '{}', NOW(), NOW()),
    ('saga-002', 'ORD-2345678901', 'COMPLETED', '{}', NOW(), NOW()),
    ('saga-003',  'ORD-3456789012', 'COMPENSATED', '{}', NOW(), NOW()),
    ('saga-004',  'ORD-4567890123', 'COMPLETED', '{}', NOW(), NOW()),
    ('saga-005',  'ORD-5678901234', 'PAYMENT_PENDING', '{}', NOW(), NOW());

-- Insert sample outbox events
INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, published, created_at, published_at)
VALUES
    ('Order', 'ORD-1234567890', 'OrderCreated', '{"orderId":"ORD-1234567890","userId":"user-001","totalAmount":299.99}', TRUE, NOW(), NOW()),
    ('Order', 'ORD-2345678901', 'OrderCreated', '{"orderId":"ORD-2345678901","userId":"user-002","totalAmount":549.50}', TRUE, NOW(), NOW()),
    ('Order', 'ORD-2345678901', 'OrderConfirmed', '{"orderId":"ORD-2345678901","paymentId":"pay-abc123"}', TRUE, NOW(), NOW()),
    ('Order', 'ORD-3456789012', 'OrderCreated', '{"orderId":"ORD-3456789012","userId":"user-001","totalAmount":129.99}', TRUE, NOW(), NOW()),
    ('Order', 'ORD-3456789012', 'OrderCancelled', '{"orderId":"ORD-3456789012","reason":"Payment failed"}', TRUE, NOW(), NOW());

-- Insert sample idempotency keys
INSERT INTO idempotency_keys (idempotency_key, response_payload, created_at, expires_at)
VALUES
    ('idem-key-001', '{"orderId":"ORD-1234567890","status":"PENDING"}', NOW(), DATE_ADD(NOW(), INTERVAL 24 HOUR)),
    ('idem-key-002', '{"orderId":"ORD-2345678901","status":"CONFIRMED"}', NOW(), DATE_ADD(NOW(), INTERVAL 24 HOUR));
