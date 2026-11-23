-- Additive platform commerce schema. No existing tables or rows are replaced.
CREATE TABLE IF NOT EXISTS commerce_product_profiles (
    product_id BIGINT PRIMARY KEY,
    category VARCHAR(24) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    request_key VARCHAR(64) UNIQUE,
    updated_at_ms BIGINT NOT NULL,
    CONSTRAINT fk_commerce_profile_product FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE TABLE IF NOT EXISTS commerce_returns (
    id VARCHAR(36) PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    buyer_id BIGINT NOT NULL,
    return_type VARCHAR(24) NOT NULL,
    reason VARCHAR(48) NOT NULL,
    customer_note VARCHAR(500) NOT NULL,
    status VARCHAR(24) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    carrier VARCHAR(40) NOT NULL DEFAULT '',
    tracking_number VARCHAR(80) NOT NULL DEFAULT '',
    decision_note VARCHAR(500) NOT NULL DEFAULT '',
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_commerce_return_order FOREIGN KEY (order_id) REFERENCES trade_order_details(order_id),
    INDEX idx_commerce_return_buyer (buyer_id,updated_at_ms),
    INDEX idx_commerce_return_status (status,updated_at_ms)
);
CREATE TABLE IF NOT EXISTS commerce_return_events (
    id VARCHAR(36) PRIMARY KEY,
    return_id VARCHAR(36) NOT NULL,
    from_status VARCHAR(24) NOT NULL,
    to_status VARCHAR(24) NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_role VARCHAR(16) NOT NULL,
    note VARCHAR(500) NOT NULL,
    created_at_ms BIGINT NOT NULL,
    CONSTRAINT fk_commerce_event_return FOREIGN KEY (return_id) REFERENCES commerce_returns(id),
    INDEX idx_commerce_event_return (return_id,created_at_ms)
);
CREATE TABLE IF NOT EXISTS commerce_refunds (
    id VARCHAR(36) PRIMARY KEY,
    return_id VARCHAR(36) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL UNIQUE,
    amount DECIMAL(14,2) NOT NULL,
    simulated BOOLEAN NOT NULL,
    created_at_ms BIGINT NOT NULL,
    CONSTRAINT fk_commerce_refund_return FOREIGN KEY (return_id) REFERENCES commerce_returns(id)
);
CREATE TABLE IF NOT EXISTS commerce_stock_movements (
    id VARCHAR(36) PRIMARY KEY,
    product_id BIGINT NOT NULL,
    quantity_delta INT NOT NULL,
    reason VARCHAR(200) NOT NULL,
    actor_id BIGINT NOT NULL,
    created_at_ms BIGINT NOT NULL,
    CONSTRAINT fk_commerce_stock_product FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE TABLE IF NOT EXISTS commerce_activity (
    id VARCHAR(36) PRIMARY KEY,
    entity_type VARCHAR(24) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    action VARCHAR(48) NOT NULL,
    summary VARCHAR(255) NOT NULL,
    actor_id BIGINT NOT NULL,
    created_at_ms BIGINT NOT NULL,
    INDEX idx_commerce_activity_created (created_at_ms)
);
