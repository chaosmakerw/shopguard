package com.codesprint.lesson0831.trade.core;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Single-item orders with database-owned stock and transitions. All writes, including
 * the legacy orders row, share one transaction. Payment is a local simulation only.
 */
public final class JdbcTradeService {
    private static final System.Logger LOG = System.getLogger(JdbcTradeService.class.getName());
    private static final SecureRandom IDS = new SecureRandom();
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
    private static final int MAX_ATTEMPTS = 4;
    private static final String PENDING = "PENDING_PAYMENT";
    private static final String PAID = "PAID";
    private static final String CANCELLED = "CANCELLED";
    private static final String ORDER_SELECT = """
            SELECT o.order_id, o.buyer_id, o.product_name, o.status,
                   d.product_id, d.quantity, d.total_amount, d.created_at_ms, d.expires_at_ms
            FROM orders o JOIN trade_order_details d
              ON d.order_id = o.order_id AND d.buyer_id = o.buyer_id
            """;
    private static final String PRODUCT_SELECT = """
            SELECT p.id, p.title, p.description, p.price, i.available_quantity
            FROM products p JOIN trade_inventory i ON i.product_id = p.id
            WHERE p.available = TRUE
            """;

    private final DataSource dataSource;
    private final Clock clock;
    private final Duration paymentWindow;

    public JdbcTradeService(DataSource dataSource, Clock clock, Duration paymentWindow) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource 不能为空");
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");
        this.paymentWindow = Objects.requireNonNull(paymentWindow, "支付窗口不能为空");
        if (paymentWindow.compareTo(Duration.ofMillis(1)) < 0
                || paymentWindow.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("支付窗口必须在 1 毫秒到 7 天之间");
        }
    }

    public List<TradeProduct> products(String keyword, int page, int size) {
        int offset = pageOffset(page, size);
        String word = keyword == null ? "" : keyword.strip();
        if (word.length() > 100 || word.chars().anyMatch(Character::isISOControl)) {
            throw invalid("搜索词最多 100 个字符，不能包含控制字符");
        }
        String pattern = "%" + word.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        return read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(PRODUCT_SELECT + """
                     AND (p.title LIKE ? ESCAPE '!' OR p.description LIKE ? ESCAPE '!')
                     ORDER BY p.id LIMIT ? OFFSET ?
                    """)) {
                statement.setString(1, pattern);
                statement.setString(2, pattern);
                statement.setInt(3, size);
                statement.setInt(4, offset);
                try (ResultSet rows = statement.executeQuery()) {
                    List<TradeProduct> products = new ArrayList<>();
                    while (rows.next()) {
                        products.add(mapProduct(rows));
                    }
                    return List.copyOf(products);
                }
            }
        });
    }

    public TradeProduct product(long productId) {
        positiveId(productId);
        return read(connection -> findProduct(connection, productId, false));
    }

    public TradeOrder create(long buyerId, long productId, int quantity, String requestKey) {
        positiveId(buyerId);
        positiveId(productId);
        key(requestKey);
        if (quantity < 1 || quantity > 20) {
            throw invalid("购买数量必须在 1 到 20 之间");
        }
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return sqlTransaction(connection -> {
                    TradeOrder previous = byRequestKey(connection, buyerId, requestKey);
                    if (previous != null) {
                        return sameRequest(previous, productId, quantity);
                    }
                    // Lock stock/product before taking the price snapshot. Recheck the key
                    // after waiting: a simultaneous retry may have reserved the last item.
                    TradeProduct product = findProduct(connection, productId, true);
                    previous = byRequestKey(connection, buyerId, requestKey);
                    if (previous != null) {
                        return sameRequest(previous, productId, quantity);
                    }
                    BigDecimal total = checkedAmount(product.price(), quantity);
                    Instant created = clock.instant().truncatedTo(ChronoUnit.MILLIS);
                    Instant expires = created.plus(paymentWindow).truncatedTo(ChronoUnit.MILLIS);
                    long orderId = nextId();
                    insertOrder(connection, orderId, buyerId, product.title());
                    insertDetails(connection, orderId, buyerId, productId, quantity,
                            total, requestKey, created, expires);
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE trade_inventory
                            SET available_quantity = available_quantity - ?,
                                reserved_quantity = reserved_quantity + ?
                            WHERE product_id = ? AND available_quantity >= ?
                              AND reserved_quantity <= ?
                            """)) {
                        statement.setInt(1, quantity);
                        statement.setInt(2, quantity);
                        statement.setLong(3, productId);
                        statement.setInt(4, quantity);
                        statement.setInt(5, Integer.MAX_VALUE - quantity);
                        if (statement.executeUpdate() != 1) {
                            throw new TradeException("OUT_OF_STOCK", 409, "商品库存不足");
                        }
                    }
                    return new TradeOrder(orderId, buyerId, productId, product.title(), quantity,
                            total, PENDING, created, expires);
                });
            } catch (SQLException failure) {
                if (duplicateKey(failure)) {
                    // A unique constraint arbitrates concurrent keys, including keys used
                    // with different products. Read only after rolling back this attempt.
                    TradeOrder winner = read(c -> byRequestKey(c, buyerId, requestKey));
                    if (winner != null) {
                        return sameRequest(winner, productId, quantity);
                    }
                    // No key winner means the random primary key collided. Retry finitely.
                } else if (!retryable(failure)) {
                    throw databaseFailure(failure);
                }
                if (attempt == MAX_ATTEMPTS - 1) {
                    throw databaseFailure(failure);
                }
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }

    public List<TradeOrder> orders(long buyerId, int page, int size) {
        positiveId(buyerId);
        int offset = pageOffset(page, size);
        return read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(ORDER_SELECT + """
                     WHERE o.buyer_id = ?
                     ORDER BY d.created_at_ms DESC, o.order_id DESC LIMIT ? OFFSET ?
                    """)) {
                statement.setLong(1, buyerId);
                statement.setInt(2, size);
                statement.setInt(3, offset);
                try (ResultSet rows = statement.executeQuery()) {
                    List<TradeOrder> orders = new ArrayList<>();
                    while (rows.next()) {
                        orders.add(mapOrder(rows));
                    }
                    return List.copyOf(orders);
                }
            }
        });
    }

    public TradeOrder order(long buyerId, long orderId) {
        positiveId(buyerId);
        positiveId(orderId);
        return read(connection -> ownedOrder(connection, buyerId, orderId, false));
    }

    public TradeOrder cancel(long buyerId, long orderId) {
        positiveId(buyerId);
        positiveId(orderId);
        return transaction(connection -> {
            TradeOrder order = ownedOrder(connection, buyerId, orderId, true);
            if (CANCELLED.equals(order.status())) {
                return order;
            }
            requirePending(order);
            return transition(connection, order, CANCELLED);
        });
    }

    public TradeOrder simulatePayment(long buyerId, long orderId, String paymentKey) {
        positiveId(buyerId);
        positiveId(orderId);
        key(paymentKey);
        PaymentOutcome outcome = transaction(connection -> {
            TradeOrder order = ownedOrder(connection, buyerId, orderId, true);
            if (!PENDING.equals(order.status()) && !PAID.equals(order.status())) {
                throw stateConflict();
            }
            bindPaymentKey(connection, buyerId, orderId, paymentKey);
            if (PAID.equals(order.status())) {
                return new PaymentOutcome(order, false);
            }
            if (!clock.instant().isBefore(order.expiresAt())) {
                return new PaymentOutcome(transition(connection, order, CANCELLED), true);
            }
            return new PaymentOutcome(transition(connection, order, PAID), false);
        });
        // Throw only after commit, otherwise releasing expired stock would be rolled back.
        if (outcome.expired()) {
            throw new TradeException("ORDER_EXPIRED", 409, "订单已超时关闭，库存已释放");
        }
        return outcome.order();
    }

    public int expireDue(int batchSize) {
        if (batchSize < 1 || batchSize > 1000) {
            throw invalid("过期处理批次必须在 1 到 1000 之间");
        }
        List<Long> ids = read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT d.order_id FROM trade_order_details d
                    JOIN orders o ON o.order_id = d.order_id AND o.buyer_id = d.buyer_id
                    WHERE o.status = 'PENDING_PAYMENT' AND d.expires_at_ms <= ?
                    ORDER BY d.expires_at_ms, d.order_id LIMIT ?
                    """)) {
                statement.setLong(1, clock.millis());
                statement.setInt(2, batchSize);
                try (ResultSet rows = statement.executeQuery()) {
                    List<Long> due = new ArrayList<>();
                    while (rows.next()) {
                        due.add(rows.getLong(1));
                    }
                    return due;
                }
            }
        });
        int expired = 0;
        for (long id : ids) {
            // Each order gets its own short transaction and a locked state recheck.
            boolean changed = transaction(connection -> {
                TradeOrder order = selectOrder(connection, id, null, true);
                if (order == null || !PENDING.equals(order.status())
                        || clock.instant().isBefore(order.expiresAt())) {
                    return false;
                }
                transition(connection, order, CANCELLED);
                return true;
            });
            if (changed) {
                expired++;
            }
        }
        return expired;
    }

    private TradeProduct findProduct(Connection connection, long productId, boolean lock) throws SQLException {
        String sql = PRODUCT_SELECT + " AND p.id = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new TradeException("PRODUCT_NOT_FOUND", 404, "商品不存在或暂不可购买");
                }
                return mapProduct(rows);
            }
        }
    }

    private TradeOrder byRequestKey(Connection connection, long buyerId, String requestKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ORDER_SELECT
                + " WHERE d.buyer_id = ? AND d.request_key = ?")) {
            statement.setLong(1, buyerId);
            statement.setString(2, requestKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? mapOrder(rows) : null;
            }
        }
    }

    private TradeOrder ownedOrder(Connection connection, long buyerId, long orderId,
                                  boolean lock) throws SQLException {
        TradeOrder order = selectOrder(connection, orderId, buyerId, lock);
        if (order == null) {
            // An inaccessible order is indistinguishable from an absent order.
            throw new TradeException("ORDER_NOT_FOUND", 404, "订单不存在");
        }
        return order;
    }

    private TradeOrder selectOrder(Connection connection, long orderId, Long buyerId,
                                   boolean lock) throws SQLException {
        String sql = ORDER_SELECT + " WHERE o.order_id = ?"
                + (buyerId == null ? "" : " AND o.buyer_id = ?") + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, orderId);
            if (buyerId != null) {
                statement.setLong(2, buyerId);
            }
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? mapOrder(rows) : null;
            }
        }
    }

    private void insertOrder(Connection connection, long id, long buyer, String title) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO orders(order_id, buyer_id, product_name, status) VALUES (?, ?, ?, ?)
                """)) {
            statement.setLong(1, id);
            statement.setLong(2, buyer);
            statement.setString(3, title);
            statement.setString(4, PENDING);
            statement.executeUpdate();
        }
    }

    private void insertDetails(Connection connection, long id, long buyer, long productId,
                               int quantity, BigDecimal total, String requestKey,
                               Instant created, Instant expires) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO trade_order_details(order_id, buyer_id, product_id, quantity,
                    total_amount, request_key, created_at_ms, expires_at_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, id);
            statement.setLong(2, buyer);
            statement.setLong(3, productId);
            statement.setInt(4, quantity);
            statement.setBigDecimal(5, total);
            statement.setString(6, requestKey);
            statement.setLong(7, created.toEpochMilli());
            statement.setLong(8, expires.toEpochMilli());
            statement.executeUpdate();
        }
    }

    private void bindPaymentKey(Connection connection, long buyerId, long orderId,
                                String paymentKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT order_id FROM trade_payment_requests WHERE buyer_id = ? AND payment_key = ?
                """)) {
            statement.setLong(1, buyerId);
            statement.setString(2, paymentKey);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    if (rows.getLong(1) != orderId) {
                        throw keyConflict();
                    }
                    return;
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO trade_payment_requests(buyer_id, payment_key, order_id) VALUES (?, ?, ?)
                """)) {
            statement.setLong(1, buyerId);
            statement.setString(2, paymentKey);
            statement.setLong(3, orderId);
            statement.executeUpdate();
        } catch (SQLException failure) {
            if (duplicateKey(failure)) {
                throw new TradeException("IDEMPOTENCY_CONFLICT", 409,
                        "请求标识已用于其他操作，请使用新的请求标识", failure);
            }
            throw failure;
        }
    }

    private TradeOrder transition(Connection connection, TradeOrder order, String target) throws SQLException {
        requirePending(order);
        String counter = PAID.equals(target) ? "sold_quantity" : "available_quantity";
        try (PreparedStatement statement = connection.prepareStatement("UPDATE trade_inventory SET "
                + "reserved_quantity = reserved_quantity - ?, " + counter + " = " + counter + " + ?"
                + " WHERE product_id = ? AND reserved_quantity >= ? AND " + counter + " <= ?")) {
            statement.setInt(1, order.quantity());
            statement.setInt(2, order.quantity());
            statement.setLong(3, order.productId());
            statement.setInt(4, order.quantity());
            statement.setInt(5, Integer.MAX_VALUE - order.quantity());
            if (statement.executeUpdate() != 1) {
                throw new TradeException("TRADE_STATE_INVALID", 500, "交易状态暂时异常，请稍后重试");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE orders SET status = ? WHERE order_id = ? AND buyer_id = ? AND status = ?
                """)) {
            statement.setString(1, target);
            statement.setLong(2, order.orderId());
            statement.setLong(3, order.buyerId());
            statement.setString(4, PENDING);
            if (statement.executeUpdate() != 1) {
                throw stateConflict();
            }
        }
        return new TradeOrder(order.orderId(), order.buyerId(), order.productId(), order.productName(),
                order.quantity(), order.totalAmount(), target, order.createdAt(), order.expiresAt());
    }

    private <T> T read(SqlWork<T> work) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            return work.run(connection);
        } catch (SQLException failure) {
            throw databaseFailure(failure);
        } finally {
            closeQuietly(connection);
        }
    }

    private <T> T transaction(SqlWork<T> work) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return sqlTransaction(work);
            } catch (SQLException failure) {
                if (!retryable(failure) || attempt == MAX_ATTEMPTS - 1) {
                    throw databaseFailure(failure);
                }
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }

    private <T> T sqlTransaction(SqlWork<T> work) throws SQLException {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            // Fresh reads after waiting on locks are needed for idempotency arbitration.
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            T result = work.run(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException | Error failure) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        } finally {
            // A close failure after a successful commit must not report a failed order.
            closeQuietly(connection);
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException failure) {
                // Do not log driver messages, which can contain SQL, URLs or credentials.
                LOG.log(System.Logger.Level.WARNING, "Trade connection cleanup failed; SQLState={0}",
                        failure.getSQLState());
            }
        }
    }

    private static TradeProduct mapProduct(ResultSet rows) throws SQLException {
        return new TradeProduct(rows.getLong("id"), rows.getString("title"),
                rows.getString("description"), rows.getBigDecimal("price"),
                rows.getInt("available_quantity"));
    }

    private static TradeOrder mapOrder(ResultSet rows) throws SQLException {
        return new TradeOrder(rows.getLong("order_id"), rows.getLong("buyer_id"),
                rows.getLong("product_id"), rows.getString("product_name"),
                rows.getInt("quantity"), rows.getBigDecimal("total_amount"), rows.getString("status"),
                Instant.ofEpochMilli(rows.getLong("created_at_ms")),
                Instant.ofEpochMilli(rows.getLong("expires_at_ms")));
    }

    private static BigDecimal checkedAmount(BigDecimal price, int quantity) {
        try {
            BigDecimal total = price.setScale(2, RoundingMode.UNNECESSARY)
                    .multiply(BigDecimal.valueOf(quantity));
            if (total.signum() <= 0 || total.compareTo(MAX_AMOUNT) > 0) {
                throw new ArithmeticException("Invalid amount");
            }
            return total;
        } catch (ArithmeticException failure) {
            throw new TradeException("PRODUCT_PRICE_INVALID", 409, "商品价格暂时异常，无法下单", failure);
        }
    }

    private static TradeOrder sameRequest(TradeOrder order, long productId, int quantity) {
        if (order.productId() != productId || order.quantity() != quantity) {
            throw keyConflict();
        }
        return order;
    }

    private static void requirePending(TradeOrder order) {
        if (!PENDING.equals(order.status())) {
            throw stateConflict();
        }
    }

    private static int pageOffset(int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 50) {
            throw invalid("页码必须在 1 到 10000 之间，每页数量必须在 1 到 50 之间");
        }
        return (page - 1) * size;
    }

    private static void positiveId(long id) {
        if (id <= 0) {
            throw invalid("编号必须是正整数");
        }
    }

    private static void key(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw invalid("请求标识必须是 8 到 64 位英文字母、数字、下划线或连字符");
        }
    }

    private static long nextId() {
        long id;
        do {
            id = IDS.nextLong() & Long.MAX_VALUE;
        } while (id == 0);
        return id;
    }

    private static boolean duplicateKey(SQLException failure) {
        return "23505".equals(failure.getSQLState()) || failure.getErrorCode() == 1062;
    }

    private static boolean retryable(SQLException failure) {
        return "40001".equals(failure.getSQLState()) || failure.getErrorCode() == 1213;
    }

    private static TradeException invalid(String message) {
        return new TradeException("INVALID_REQUEST", 400, message);
    }

    private static TradeException keyConflict() {
        return new TradeException("IDEMPOTENCY_CONFLICT", 409, "请求标识已用于不同的订单内容");
    }

    private static TradeException stateConflict() {
        return new TradeException("ORDER_STATE_CONFLICT", 409, "订单当前状态不支持此操作");
    }

    private static TradeException databaseFailure(SQLException failure) {
        return new TradeException("TRADE_UNAVAILABLE", 503, "交易服务暂时不可用，请稍后重试", failure);
    }

    private record PaymentOutcome(TradeOrder order, boolean expired) {
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
