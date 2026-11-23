package com.codesprint.lesson0831.trade.core;

import com.codesprint.lesson0831.order.JdbcOrderService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Real JDBC/database tests. No Spring server, model, payment provider or external network. */
class JdbcTradeServiceIntegrationTest {
    private DataSource database;
    private MutableClock clock;
    private JdbcTradeService service;

    @BeforeEach
    void setUp() throws SQLException {
        String mysqlUrl = System.getProperty("shopguard.test.mysql.url");
        boolean mysql = mysqlUrl != null && !mysqlUrl.isBlank();
        if (mysql) {
            requireIsolatedMysql(mysqlUrl);
            database = new DriverManagerDataSource(mysqlUrl,
                    System.getProperty("shopguard.test.mysql.username", "shopguard_test"),
                    System.getProperty("shopguard.test.mysql.password", ""));
        } else {
            JdbcDataSource h2 = new JdbcDataSource();
            h2.setURL("jdbc:h2:mem:trade_" + System.nanoTime()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            h2.setUser("sa");
            database = h2;
        }
        new ResourceDatabasePopulator(
                new ClassPathResource(mysql ? "db/schema-mysql.sql" : "db/schema.sql"),
                new ClassPathResource(mysql ? "db/trade-schema-mysql.sql" : "db/trade-schema.sql"))
                .execute(database);
        // Destructive reset is allowed only for a fresh H2 database or the explicitly
        // named loopback shopguard_acceptance test database, never a project database.
        sql("DELETE FROM trade_payment_requests");
        sql("DELETE FROM trade_order_details");
        sql("DELETE FROM trade_inventory");
        sql("DELETE FROM orders");
        sql("DELETE FROM products");
        sql("INSERT INTO products VALUES (1, 'Java 实战', '100% 独立实践', 59.90, TRUE)");
        sql("INSERT INTO products VALUES (2, '数据库基础', '关系型数据库', 39.95, TRUE)");
        sql("INSERT INTO products VALUES (3, '下架商品', '不可购买', 1.00, FALSE)");
        sql("INSERT INTO trade_inventory VALUES (1, 3, 0, 0), (2, 3, 0, 0), (3, 3, 0, 0)");
        clock = new MutableClock(Instant.parse("2026-10-23T03:00:00Z"));
        service = new JdbcTradeService(database, clock, Duration.ofMinutes(15));
    }

    @Test
    void searchesAndPaginatesOnlyPurchasableProductsWithLiteralWildcards() {
        assertEquals(List.of(1L, 2L), service.products(null, 1, 50).stream()
                .map(TradeProduct::productId).toList());
        assertEquals(2L, service.products("", 2, 1).get(0).productId());
        assertTrue(service.products("", 3, 1).isEmpty());
        assertEquals(1L, service.products("%", 1, 10).get(0).productId());
        assertTrue(service.products("_", 1, 10).isEmpty());
        assertEquals(new BigDecimal("59.90"), service.product(1).price());
        error("PRODUCT_NOT_FOUND", () -> service.product(3));
        error("PRODUCT_NOT_FOUND", () -> service.product(999));
    }

    @Test
    void createsExactAmountReservesStockAndRemainsVisibleToExistingOrderReader() throws SQLException {
        TradeOrder order = service.create(1001, 1, 2, "create-0001");
        assertTrue(order.orderId() > 0);
        assertEquals("PENDING_PAYMENT", order.status());
        assertEquals(new BigDecimal("119.80"), order.totalAmount());
        assertEquals(clock.instant().plusSeconds(900), order.expiresAt());
        assertStock(1, 1, 2, 0);
        assertEquals(order, service.order(1001, order.orderId()));
        assertEquals(List.of(order), service.orders(1001, 1, 10));
        assertEquals(order.orderId(), new JdbcOrderService(database)
                .listMyOrders(1001L).get(0).orderId());
        assertEquals(1, scalar("SELECT COUNT(*) FROM orders"));
    }

    @Test
    void reservesFinalItemAndRollsBackAnOutOfStockRequest() throws SQLException {
        sql("UPDATE trade_inventory SET available_quantity = 1 WHERE product_id = 1");
        service.create(1001, 1, 1, "first-key");
        error("OUT_OF_STOCK", () -> service.create(1002, 1, 1, "second-key"));
        assertStock(1, 0, 1, 0);
        assertEquals(1, scalar("SELECT COUNT(*) FROM orders"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM trade_order_details"));
    }

    @Test
    void supportsMaximumQuantityAndExactLargeDecimalPrices() throws SQLException {
        sql("UPDATE products SET price = 9999999999.99 WHERE id = 1");
        sql("UPDATE trade_inventory SET available_quantity = 20 WHERE product_id = 1");
        assertEquals(new BigDecimal("199999999999.80"),
                service.create(1001, 1, 20, "large-amount").totalAmount());
        assertStock(1, 0, 20, 0);
    }

    @Test
    void replayReturnsOriginalPriceAndStateWithoutReservingAgain() throws SQLException {
        TradeOrder first = service.create(1001, 1, 3, "stable-key");
        sql("UPDATE products SET price = 99.99, available = FALSE WHERE id = 1");
        assertEquals(first, service.create(1001, 1, 3, "stable-key"));
        service.cancel(1001, first.orderId());
        assertEquals("CANCELLED", service.create(1001, 1, 3, "stable-key").status());
        assertStock(1, 3, 0, 0);
    }

    @Test
    void rejectsDifferentPayloadForExistingKey() throws SQLException {
        service.create(1001, 1, 1, "stable-key");
        error("IDEMPOTENCY_CONFLICT", () -> service.create(1001, 1, 2, "stable-key"));
        error("IDEMPOTENCY_CONFLICT", () -> service.create(1001, 2, 1, "stable-key"));
        assertStock(1, 2, 1, 0);
        assertStock(2, 3, 0, 0);
        assertEquals(1, scalar("SELECT COUNT(*) FROM orders"));
    }

    @Test
    void isolatesOwnersAndScopesCreateAndPaymentKeysToBuyer() throws SQLException {
        TradeOrder mine = service.create(1001, 1, 1, "shared-key");
        TradeOrder theirs = service.create(1002, 1, 1, "shared-key");
        assertNotEquals(mine.orderId(), theirs.orderId());
        assertEquals(List.of(mine), service.orders(1001, 1, 50));
        assertTrue(service.orders(1003, 1, 50).isEmpty());
        error("ORDER_NOT_FOUND", () -> service.order(1002, mine.orderId()));
        error("ORDER_NOT_FOUND", () -> service.cancel(1002, mine.orderId()));
        error("ORDER_NOT_FOUND", () -> service.simulatePayment(1002, mine.orderId(), "payment-key"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM trade_payment_requests"));
        service.simulatePayment(1001, mine.orderId(), "payment-key");
        service.simulatePayment(1002, theirs.orderId(), "payment-key");
        assertStock(1, 1, 0, 2);
    }

    @Test
    void treatsKeysAsCaseSensitiveIncludingOnMysql() throws SQLException {
        TradeOrder upper = service.create(1001, 1, 1, "RequestKey");
        TradeOrder lower = service.create(1001, 1, 1, "requestkey");
        assertNotEquals(upper.orderId(), lower.orderId());
        service.simulatePayment(1001, upper.orderId(), "PaymentKey");
        service.simulatePayment(1001, lower.orderId(), "paymentkey");
        assertStock(1, 1, 0, 2);
    }

    @Test
    void simultaneousBuyersCannotOversellLastItem() throws Exception {
        sql("UPDATE trade_inventory SET available_quantity = 1 WHERE product_id = 1");
        AtomicInteger buyers = new AtomicInteger(1000);
        List<Object> results = together(12, () -> attempt(() -> {
            int buyer = buyers.incrementAndGet();
            return service.create(buyer, 1, 1, "request-" + buyer);
        }));
        assertEquals(1, results.stream().filter(TradeOrder.class::isInstance).count());
        assertEquals(11, results.stream().filter(r -> isError(r, "OUT_OF_STOCK")).count());
        assertStock(1, 0, 1, 0);
        assertEquals(1, scalar("SELECT COUNT(*) FROM orders"));
    }

    @Test
    void simultaneousSameKeyReturnsOneOrderEvenWhenItConsumesFinalStock() throws Exception {
        sql("UPDATE trade_inventory SET available_quantity = 1 WHERE product_id = 1");
        List<TradeOrder> orders = together(12, () -> service.create(1001, 1, 1, "concurrent-key"));
        assertEquals(1, orders.stream().map(TradeOrder::orderId).distinct().count());
        assertStock(1, 0, 1, 0);
        assertEquals(1, scalar("SELECT COUNT(*) FROM orders"));
    }

    @Test
    void concurrentDifferentProductsCannotReuseOneCreateKey() throws Exception {
        AtomicInteger product = new AtomicInteger();
        List<Object> results = together(2, () -> attempt(() ->
                service.create(1001, product.incrementAndGet(), 1, "one-shared-key")));
        assertEquals(1, results.stream().filter(TradeOrder.class::isInstance).count());
        assertEquals(1, results.stream().filter(r -> isError(r, "IDEMPOTENCY_CONFLICT")).count());
        assertEquals(1, scalar("SELECT COUNT(*) FROM orders"));
        assertEquals(1, scalar("SELECT SUM(reserved_quantity) FROM trade_inventory"));
    }

    @Test
    void repeatedPaymentWithSameOrDifferentKeyDoesNotSellAgain() throws SQLException {
        TradeOrder order = service.create(1001, 1, 2, "payment-order");
        TradeOrder paid = service.simulatePayment(1001, order.orderId(), "payment-001");
        assertEquals("PAID", paid.status());
        assertEquals(paid, service.simulatePayment(1001, order.orderId(), "payment-001"));
        assertEquals(paid, service.simulatePayment(1001, order.orderId(), "payment-002"));
        error("ORDER_STATE_CONFLICT", () -> service.cancel(1001, order.orderId()));
        assertStock(1, 1, 0, 2);
    }

    @Test
    void paymentKeyCannotBeReusedForAnotherOrder() throws SQLException {
        TradeOrder first = service.create(1001, 1, 1, "order-first");
        TradeOrder second = service.create(1001, 2, 1, "order-second");
        service.simulatePayment(1001, first.orderId(), "same-payment");
        error("IDEMPOTENCY_CONFLICT", () -> service.simulatePayment(1001, second.orderId(), "same-payment"));
        assertEquals("PENDING_PAYMENT", service.order(1001, second.orderId()).status());
        assertStock(2, 2, 1, 0);
    }

    @Test
    void concurrentPaymentsSellOnce() throws Exception {
        TradeOrder order = service.create(1001, 1, 2, "order-to-pay");
        List<TradeOrder> paid = together(10,
                () -> service.simulatePayment(1001, order.orderId(), "same-payment"));
        assertTrue(paid.stream().allMatch(o -> o.status().equals("PAID")));
        assertStock(1, 1, 0, 2);
        assertEquals(1, scalar("SELECT COUNT(*) FROM trade_payment_requests"));
    }

    @Test
    void concurrentCrossOrderPaymentKeyCollisionRollsBackLoser() throws Exception {
        List<TradeOrder> orders = List.of(service.create(1001, 1, 1, "first-order"),
                service.create(1001, 2, 1, "second-order"));
        AtomicInteger index = new AtomicInteger();
        List<Object> results = together(2, () -> attempt(() -> service.simulatePayment(1001,
                orders.get(index.getAndIncrement()).orderId(), "shared-payment")));
        assertEquals(1, results.stream().filter(TradeOrder.class::isInstance).count());
        assertEquals(1, results.stream().filter(r -> isError(r, "IDEMPOTENCY_CONFLICT")).count());
        assertEquals(1, scalar("SELECT SUM(sold_quantity) FROM trade_inventory"));
        assertEquals(1, scalar("SELECT SUM(reserved_quantity) FROM trade_inventory"));
    }

    @Test
    void repeatedCancellationReleasesStockOnlyOnce() throws Exception {
        TradeOrder order = service.create(1001, 1, 2, "order-to-cancel");
        List<TradeOrder> cancelled = together(8, () -> service.cancel(1001, order.orderId()));
        assertTrue(cancelled.stream().allMatch(o -> o.status().equals("CANCELLED")));
        error("ORDER_STATE_CONFLICT", () -> service.simulatePayment(1001, order.orderId(), "pay-cancelled"));
        assertStock(1, 3, 0, 0);
    }

    @Test
    void paymentAndCancellationRaceHasOneLegalWinner() throws Exception {
        TradeOrder order = service.create(1001, 1, 2, "race-order");
        AtomicInteger operation = new AtomicInteger();
        List<Object> results = together(2, () -> attempt(() -> operation.getAndIncrement() == 0
                ? service.simulatePayment(1001, order.orderId(), "race-payment")
                : service.cancel(1001, order.orderId())));
        assertEquals(1, results.stream().filter(TradeOrder.class::isInstance).count());
        assertEquals(1, results.stream().filter(r -> isError(r, "ORDER_STATE_CONFLICT")).count());
        String status = service.order(1001, order.orderId()).status();
        if (status.equals("PAID")) {
            assertStock(1, 1, 0, 2);
        } else {
            assertEquals("CANCELLED", status);
            assertStock(1, 3, 0, 0);
        }
    }

    @Test
    void paymentBeforeDeadlineSucceedsButAtDeadlineCommitsClosureBeforeError() throws SQLException {
        TradeOrder before = service.create(1001, 1, 1, "before-deadline");
        TradeOrder boundary = service.create(1001, 1, 1, "exact-deadline");
        clock.set(before.expiresAt().minusMillis(1));
        assertEquals("PAID", service.simulatePayment(1001, before.orderId(), "pay-before").status());
        clock.set(boundary.expiresAt());
        error("ORDER_EXPIRED", () -> service.simulatePayment(1001, boundary.orderId(), "pay-on-time"));
        assertEquals("CANCELLED", service.order(1001, boundary.orderId()).status());
        assertEquals(0, service.expireDue(10));
        assertStock(1, 2, 0, 1);
    }

    @Test
    void expiryIsBoundedAndDoesNotTouchPaidOrFutureOrders() throws SQLException {
        TradeOrder paid = service.create(1001, 1, 1, "paid-order");
        service.simulatePayment(1001, paid.orderId(), "paid-before");
        service.create(1001, 1, 1, "expire-first");
        service.create(1001, 1, 1, "expire-second");
        clock.set(paid.expiresAt().minusMillis(1));
        assertEquals(0, service.expireDue(10));
        TradeOrder future = service.create(1001, 2, 1, "future-order");
        clock.set(paid.expiresAt());
        assertEquals(1, service.expireDue(1));
        assertEquals(1, service.expireDue(1));
        assertEquals(0, service.expireDue(10));
        assertEquals("PENDING_PAYMENT", service.order(1001, future.orderId()).status());
        assertStock(1, 2, 0, 1);
    }

    @Test
    void concurrentExpiryWorkersCloseOrderOnlyOnce() throws Exception {
        TradeOrder order = service.create(1001, 1, 2, "expiry-workers");
        clock.set(order.expiresAt());
        assertEquals(1, together(6, () -> service.expireDue(10)).stream().mapToInt(Integer::intValue).sum());
        assertStock(1, 3, 0, 0);
    }

    @Test
    void expiryAndPaymentRaceAtDeadlineNeverProducesPaidOrder() throws Exception {
        TradeOrder order = service.create(1001, 1, 2, "expiry-payment");
        clock.set(order.expiresAt());
        AtomicInteger operation = new AtomicInteger();
        List<Object> outcomes = together(2, () -> {
            if (operation.getAndIncrement() == 0) {
                return service.expireDue(10);
            }
            return attempt(() -> service.simulatePayment(1001, order.orderId(), "late-payment"));
        });
        assertTrue(outcomes.stream().anyMatch(r -> isError(r, "ORDER_EXPIRED")
                || isError(r, "ORDER_STATE_CONFLICT")));
        assertEquals("CANCELLED", service.order(1001, order.orderId()).status());
        assertStock(1, 3, 0, 0);
    }

    @Test
    void failureAfterInventoryWriteRollsBackOrderAndStockAndReleasesConnection() throws SQLException {
        FaultDataSource faulty = new FaultDataSource(database, "UPDATE trade_inventory", true, false);
        JdbcTradeService tested = new JdbcTradeService(faulty, clock, Duration.ofMinutes(15));
        TradeException failure = error("TRADE_UNAVAILABLE", () -> tested.create(1001, 1, 2, "failed-order"));
        assertNotNull(failure.getCause());
        assertFalse(failure.getMessage().contains("secret"));
        assertEquals(0, faulty.openConnections.get());
        assertStock(1, 3, 0, 0);
        assertEquals(0, scalar("SELECT COUNT(*) FROM orders"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM trade_order_details"));
        assertEquals("PENDING_PAYMENT", tested.create(1001, 1, 2, "failed-order").status());
        assertEquals(0, faulty.openConnections.get());
    }

    @Test
    void paymentFailureAfterStockChangeRollsBackStockStatusAndPaymentKey() throws SQLException {
        TradeOrder order = service.create(1001, 1, 2, "payment-fault");
        FaultDataSource faulty = new FaultDataSource(database, "UPDATE orders SET status", false, false);
        JdbcTradeService tested = new JdbcTradeService(faulty, clock, Duration.ofMinutes(15));
        error("TRADE_UNAVAILABLE", () -> tested.simulatePayment(1001, order.orderId(), "retry-payment"));
        assertStock(1, 1, 2, 0);
        assertEquals("PENDING_PAYMENT", service.order(1001, order.orderId()).status());
        assertEquals(0, scalar("SELECT COUNT(*) FROM trade_payment_requests"));
        assertEquals(0, faulty.openConnections.get());
        assertEquals("PAID", tested.simulatePayment(1001, order.orderId(), "retry-payment").status());
        assertStock(1, 1, 0, 2);
    }

    @Test
    void failedConnectionBorrowIsSafeAndNextCallCanRecover() {
        FaultDataSource faulty = new FaultDataSource(database, null, false, false);
        faulty.failBorrow.set(true);
        JdbcTradeService tested = new JdbcTradeService(faulty, clock, Duration.ofMinutes(15));
        TradeException failure = error("TRADE_UNAVAILABLE", () -> tested.product(1));
        assertEquals(503, failure.httpStatus());
        assertFalse(failure.getMessage().contains("password"));
        assertNotNull(failure.getCause());
        assertEquals(1, tested.product(1).productId());
        assertEquals(0, faulty.openConnections.get());
    }

    @Test
    void connectionCloseFailureDoesNotReportFailureAfterCommit() throws SQLException {
        FaultDataSource faulty = new FaultDataSource(database, null, false, true);
        JdbcTradeService tested = new JdbcTradeService(faulty, clock, Duration.ofMinutes(15));
        TradeOrder order = tested.create(1001, 1, 2, "close-failure");
        assertEquals(order, service.order(1001, order.orderId()));
        assertStock(1, 1, 2, 0);
        assertEquals(0, faulty.openConnections.get());
        assertEquals(order, tested.create(1001, 1, 2, "close-failure"));
    }

    @Test
    void rejectsBadInputsBeforeBorrowingAConnection() {
        AtomicInteger borrows = new AtomicInteger();
        DataSource forbidden = new AbstractDataSource() {
            @Override public Connection getConnection() {
                borrows.incrementAndGet();
                throw new AssertionError("Invalid input must not reach database");
            }
            @Override public Connection getConnection(String username, String password) {
                return getConnection();
            }
        };
        JdbcTradeService tested = new JdbcTradeService(forbidden, clock, Duration.ofMinutes(15));
        List<Runnable> bad = List.of(
                () -> tested.products("x", 0, 10), () -> tested.products("", 10001, 10),
                () -> tested.products("", 1, 0), () -> tested.products("", 1, 51),
                () -> tested.products("x".repeat(101), 1, 10),
                () -> tested.products("x\u0000y", 1, 10), () -> tested.product(0),
                () -> tested.create(0, 1, 1, "valid-key"), () -> tested.create(1, -1, 1, "valid-key"),
                () -> tested.create(1, 1, 0, "valid-key"), () -> tested.create(1, 1, 21, "valid-key"),
                () -> tested.create(1, 1, 1, null), () -> tested.create(1, 1, 1, "short"),
                () -> tested.create(1, 1, 1, "x".repeat(65)),
                () -> tested.create(1, 1, 1, "中文request"),
                () -> tested.create(1, 1, 1, "key space"),
                () -> tested.orders(0, 1, 10), () -> tested.orders(1, 1, 51),
                () -> tested.order(0, 1), () -> tested.order(1, -1),
                () -> tested.cancel(0, 1), () -> tested.cancel(1, 0),
                () -> tested.simulatePayment(0, 1, "valid-key"),
                () -> tested.simulatePayment(1, 0, "valid-key"),
                () -> tested.simulatePayment(1, 1, "bad key!"),
                () -> tested.expireDue(0), () -> tested.expireDue(1001));
        bad.forEach(action -> assertEquals(400, error("INVALID_REQUEST", action).httpStatus()));
        assertEquals(0, borrows.get());
    }

    @Test
    void invalidPriceStopsBeforeOrderAndStockChanges() throws SQLException {
        sql("UPDATE products SET price = 0 WHERE id = 1");
        error("PRODUCT_PRICE_INVALID", () -> service.create(1001, 1, 1, "bad-price"));
        assertStock(1, 3, 0, 0);
        assertEquals(0, scalar("SELECT COUNT(*) FROM orders"));
    }

    @Test
    void invalidReservedStockDoesNotMarkOrderPaidOrKeepPaymentKey() throws SQLException {
        TradeOrder order = service.create(1001, 1, 1, "corrupt-stock");
        sql("UPDATE trade_inventory SET reserved_quantity = 0 WHERE product_id = 1");
        error("TRADE_STATE_INVALID", () -> service.simulatePayment(1001, order.orderId(), "pay-corrupt"));
        assertEquals("PENDING_PAYMENT", service.order(1001, order.orderId()).status());
        assertEquals(0, scalar("SELECT COUNT(*) FROM trade_payment_requests"));
    }

    @Test
    void constructorRejectsInvalidWindowsAndMysqlGuardRejectsNonAcceptanceTargets() {
        assertThrows(IllegalArgumentException.class,
                () -> new JdbcTradeService(database, clock, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new JdbcTradeService(database, clock, Duration.ofDays(8)));
        assertThrows(IllegalArgumentException.class,
                () -> requireIsolatedMysql("jdbc:mysql://example.com/shopguard_acceptance"));
        assertThrows(IllegalArgumentException.class,
                () -> requireIsolatedMysql("jdbc:mysql://127.0.0.1/shopguard"));
        assertThrows(IllegalArgumentException.class,
                () -> requireIsolatedMysql("jdbc:mysql://127.0.0.1/shopguard_acceptance_extra"));
    }

    private void assertStock(long productId, int available, int reserved, int sold) throws SQLException {
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection
                .prepareStatement("SELECT * FROM trade_inventory WHERE product_id = ?")) {
            statement.setLong(1, productId);
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                assertEquals(available, row.getInt("available_quantity"));
                assertEquals(reserved, row.getInt("reserved_quantity"));
                assertEquals(sold, row.getInt("sold_quantity"));
            }
        }
    }

    private void sql(String sql) throws SQLException {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private int scalar(String sql) throws SQLException {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(sql)) {
            assertTrue(row.next());
            return row.getInt(1);
        }
    }

    private static TradeException error(String code, Runnable action) {
        TradeException failure = assertThrows(TradeException.class, action::run);
        assertEquals(code, failure.code());
        return failure;
    }

    private static Object attempt(Supplier<TradeOrder> operation) {
        try {
            return operation.get();
        } catch (TradeException failure) {
            return failure;
        }
    }

    private static boolean isError(Object result, String code) {
        return result instanceof TradeException failure && failure.code().equals(code);
    }

    private static <T> List<T> together(int count, Supplier<T> action) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test start timed out");
                    }
                    return action.get();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static void requireIsolatedMysql(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("Only isolated MySQL acceptance URLs are allowed");
        }
        URI uri = URI.create(jdbcUrl.substring(5));
        if (!Set.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost())
                || !"/shopguard_acceptance".equals(uri.getPath()) || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("MySQL tests require loopback shopguard_acceptance database");
        }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;
        private MutableClock(Instant now) { this.now = now; }
        void set(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) { throw new IllegalArgumentException("UTC only"); }
            return this;
        }
        @Override public Instant instant() { return now; }
    }

    /** Injects a one-shot driver fault while retaining real transaction and rollback behavior. */
    private static final class FaultDataSource extends AbstractDataSource {
        private final DataSource delegate;
        private final String sqlMarker;
        private final boolean failAfterExecute;
        private final AtomicBoolean writeFault;
        private final AtomicBoolean closeFault;
        private final AtomicBoolean failBorrow = new AtomicBoolean();
        private final AtomicInteger openConnections = new AtomicInteger();

        private FaultDataSource(DataSource delegate, String sqlMarker, boolean after, boolean close) {
            this.delegate = delegate;
            this.sqlMarker = sqlMarker;
            this.failAfterExecute = after;
            this.writeFault = new AtomicBoolean(sqlMarker != null);
            this.closeFault = new AtomicBoolean(close);
        }

        @Override public Connection getConnection() throws SQLException {
            if (failBorrow.compareAndSet(true, false)) { throw injectedFailure(); }
            Connection actual = delegate.getConnection();
            openConnections.incrementAndGet();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        try {
                            if (method.getName().equals("close")) {
                                try { actual.close(); } finally { openConnections.decrementAndGet(); }
                                if (closeFault.compareAndSet(true, false)) { throw injectedFailure(); }
                                return null;
                            }
                            Object result = method.invoke(actual, args);
                            if (method.getName().equals("prepareStatement") && sqlMarker != null
                                    && ((String) args[0]).contains(sqlMarker)) {
                                PreparedStatement statement = (PreparedStatement) result;
                                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                                        new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                                            boolean fault = m.getName().equals("executeUpdate")
                                                    && writeFault.compareAndSet(true, false);
                                            if (fault && !failAfterExecute) { throw injectedFailure(); }
                                            try {
                                                Object value = m.invoke(statement, a);
                                                if (fault) { throw injectedFailure(); }
                                                return value;
                                            } catch (InvocationTargetException e) { throw e.getCause(); }
                                        });
                            }
                            return result;
                        } catch (InvocationTargetException e) { throw e.getCause(); }
                    });
        }

        @Override public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        private SQLException injectedFailure() {
            return new SQLException("injected driver failure password=secret", "HY000");
        }
    }
}
