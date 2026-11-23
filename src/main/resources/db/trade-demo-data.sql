-- Only the explicitly selected trade-demo profile executes this file.
-- Existing inventory/order rows are never reset on restart.
INSERT INTO products (id, title, description, price, available)
SELECT 9001, 'Java 核心技术 · 上卷', '本地演示商品。书页完整，适合学习 Java 基础与集合。', 39.00, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 9001);
INSERT INTO products (id, title, description, price, available)
SELECT 9002, '可折叠阅读灯', '本地演示商品。三档亮度，适合宿舍书桌。', 25.00, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 9002);
INSERT INTO products (id, title, description, price, available)
SELECT 9003, '数据库系统概念', '本地演示商品。只有一件，可观察库存争抢与取消归还。', 32.50, TRUE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 9003);
INSERT INTO trade_inventory (product_id, available_quantity, reserved_quantity, sold_quantity)
SELECT 9001, 5, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM trade_inventory WHERE product_id = 9001);
INSERT INTO trade_inventory (product_id, available_quantity, reserved_quantity, sold_quantity)
SELECT 9002, 3, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM trade_inventory WHERE product_id = 9002);
INSERT INTO trade_inventory (product_id, available_quantity, reserved_quantity, sold_quantity)
SELECT 9003, 1, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM trade_inventory WHERE product_id = 9003);
