-- Initial MySQL 8 schema; startup never replaces existing data or inserts demo rows.
CREATE TABLE IF NOT EXISTS products (
    id BIGINT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    available BOOLEAN NOT NULL
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS orders (
    order_id BIGINT PRIMARY KEY,
    buyer_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    INDEX idx_orders_buyer_status (buyer_id, status)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS conversation_messages (
    user_id VARCHAR(100) NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    message_sequence BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    PRIMARY KEY (user_id, session_id, message_sequence)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
