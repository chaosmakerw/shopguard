package com.codesprint.lesson0831.order;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcOrderServiceIntegrationTest {

    private JdbcDataSource dataSource;
    private JdbcOrderService service;

    @BeforeEach
    void setUp() {
        dataSource =
                new JdbcDataSource();

        dataSource.setURL(
                "jdbc:h2:mem:orders_"
                        + System.nanoTime()
                        + ";MODE=MySQL;"
                        + "DB_CLOSE_DELAY=-1"
        );

        dataSource.setUser("sa");
        dataSource.setPassword("");

        createTableAndInsertData();

        service =
                new JdbcOrderService(
                        dataSource
                );
    }

    @Test
    void directLookupIncludesCancelledOrdersAndPreservesObjectPermission() {
        assertEquals("CANCELLED", service.findMyOrder(1001L, 2L).orElseThrow().status());
        assertTrue(service.findMyOrder(1002L, 2L).isEmpty());
        assertTrue(service.findMyOrder(1001L, 999L).isEmpty());
    }

    @Test
    void shouldReturnOnlyCurrentUsersNonCancelledOrders() {
        List<OrderSummary> results =
                service.listMyOrders(1001L);

        assertEquals(2, results.size());

        OrderSummary first =
                results.get(0);

        assertEquals(
                Long.valueOf(1L),
                first.orderId()
        );

        assertEquals(
                Long.valueOf(1001L),
                first.buyerId()
        );

        assertEquals(
                "Java编程思想",
                first.productName()
        );

        assertEquals(
                "PAID",
                first.status()
        );

        OrderSummary second =
                results.get(1);

        assertEquals(
                Long.valueOf(4L),
                second.orderId()
        );

        assertEquals(
                Long.valueOf(1001L),
                second.buyerId()
        );

        assertEquals(
                "数据库基础",
                second.productName()
        );

        assertEquals(
                "CREATED",
                second.status()
        );
    }

    @Test
    void shouldNotReturnAnotherUsersOrders() {
        List<OrderSummary> results =
                service.listMyOrders(1002L);

        assertEquals(1, results.size());

        OrderSummary order =
                results.get(0);

        assertEquals(
                Long.valueOf(3L),
                order.orderId()
        );

        assertEquals(
                Long.valueOf(1002L),
                order.buyerId()
        );

        assertEquals(
                "数据结构",
                order.productName()
        );
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoOrders() {
        List<OrderSummary> results =
                service.listMyOrders(999L);

        assertTrue(results.isEmpty());
    }

    private void createTableAndInsertData() {
        executeSql("""
                CREATE TABLE orders(
                    order_id BIGINT PRIMARY KEY,
                    buyer_id BIGINT NOT NULL,
                    product_name VARCHAR(200) NOT NULL,
                    status VARCHAR(30) NOT NULL
                )
                """);

        executeSql("""
                INSERT INTO orders(
                    order_id,
                    buyer_id,
                    product_name,
                    status
                )
                VALUES
                    (
                        1,
                        1001,
                        'Java编程思想',
                        'PAID'
                    ),
                    (
                        2,
                        1001,
                        'Spring Boot 实战',
                        'CANCELLED'
                    ),
                    (
                        3,
                        1002,
                        '数据结构',
                        'CREATED'
                    ),
                    (
                        4,
                        1001,
                        '数据库基础',
                        'CREATED'
                    )
                """);
    }

    private void executeSql(
            String sql
    ) {
        try (
                Connection connection =
                        dataSource.getConnection();

                Statement statement =
                        connection.createStatement()
        ) {
            statement.executeUpdate(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "准备测试数据库失败",
                    exception
            );
        }
    }
}
