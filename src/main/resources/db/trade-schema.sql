-- Additive trade tables. Existing products/orders and their readers remain compatible.
-- No production seed data: inventory must be provisioned explicitly.
CREATE TABLE IF NOT EXISTS trade_inventory (
    product_id BIGINT PRIMARY KEY,
    available_quantity INT NOT NULL,
    reserved_quantity INT NOT NULL DEFAULT 0,
    sold_quantity INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_trade_inventory_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_trade_inventory_nonnegative CHECK (
        available_quantity >= 0 AND reserved_quantity >= 0 AND sold_quantity >= 0
    )
);

CREATE TABLE IF NOT EXISTS trade_order_details (
    order_id BIGINT PRIMARY KEY,
    buyer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    total_amount DECIMAL(14, 2) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    created_at_ms BIGINT NOT NULL,
    expires_at_ms BIGINT NOT NULL,
    CONSTRAINT fk_trade_details_order FOREIGN KEY (order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_trade_details_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uq_trade_create_key UNIQUE (buyer_id, request_key),
    CONSTRAINT ck_trade_details_quantity CHECK (quantity BETWEEN 1 AND 20),
    CONSTRAINT ck_trade_details_amount CHECK (total_amount > 0),
    CONSTRAINT ck_trade_details_deadline CHECK (expires_at_ms > created_at_ms),
    INDEX idx_trade_buyer_created (buyer_id, created_at_ms, order_id),
    INDEX idx_trade_expiry (expires_at_ms, order_id)
);

CREATE TABLE IF NOT EXISTS trade_payment_requests (
    buyer_id BIGINT NOT NULL,
    payment_key VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    PRIMARY KEY (buyer_id, payment_key),
    CONSTRAINT fk_trade_payment_order FOREIGN KEY (order_id) REFERENCES trade_order_details(order_id)
);
