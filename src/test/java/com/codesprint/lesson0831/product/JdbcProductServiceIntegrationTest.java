package com.codesprint.lesson0831.product;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcProductServiceIntegrationTest {

    private JdbcDataSource dataSource;
    private JdbcProductService service;

    @BeforeEach
    void setUp() {
        dataSource =
                new JdbcDataSource();

        dataSource.setURL(
                "jdbc:h2:mem:product_"
                        + System.nanoTime()
                        + ";MODE=MySQL;"
                        + "DB_CLOSE_DELAY=-1"
        );

        dataSource.setUser("sa");
        dataSource.setPassword("");

        createTableAndInsertData();

        service =
                new JdbcProductService(
                        dataSource
                );
    }

    @Test
    void shouldSearchProductsFromRealDatabase() {
        List<ProductSummary> results =
                service.search(
                        new ProductSearchRequest(
                                "Java",
                                1,
                                10,
                                "price_asc"
                        )
                );

        assertEquals(1, results.size());

        ProductSummary product =
                results.get(0);

        assertEquals(1L, product.id());

        assertEquals(
                "Java 入门书",
                product.title()
        );

        assertEquals(
                new BigDecimal("59.90"),
                product.price()
        );
    }

    @Test
    void shouldReadProductDetailFromRealDatabase() {
        ProductDetail detail =
                service.getDetail(2L);

        assertEquals(2L, detail.id());

        assertEquals(
                "Spring Boot 实战书",
                detail.title()
        );

        assertEquals(
                "学习 Spring Boot Web 开发",
                detail.description()
        );

        assertEquals(
                new BigDecimal("89.90"),
                detail.price()
        );

        assertTrue(detail.available());
    }

    @Test
    void shouldHideUnavailableProductsFromSearch() {
        insertUnavailableProduct();

        List<ProductSummary> results =
                service.search(
                        new ProductSearchRequest(
                                "Java",
                                1,
                                10,
                                "price_asc"
                        )
                );

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).id());
    }

    @Test
    void shouldHideUnavailableProductDetail() {
        insertUnavailableProduct();

        ProductNotFoundException exception =
                assertThrows(
                        ProductNotFoundException.class,
                        () -> service.getDetail(4L)
                );

        assertEquals(
                "商品不存在",
                exception.getMessage()
        );
    }

    @Test
    void shouldObserveRowsInsertedAfterServiceWasCreated() {
        executeSql("""
                INSERT INTO products(
                    id,
                    title,
                    description,
                    price,
                    available
                )
                VALUES(
                    4,
                    'Java 性能优化',
                    'Java 性能分析入门',
                    49.90,
                    TRUE
                )
                """);

        List<ProductSummary> results =
                service.search(
                        new ProductSearchRequest(
                                "Java",
                                1,
                                10,
                                "price_asc"
                        )
                );

        assertEquals(2, results.size());

        assertEquals(
                "Java 性能优化",
                results.get(0).title()
        );

        assertEquals(
                new BigDecimal("49.90"),
                results.get(0).price()
        );

        assertEquals(
                "Java 入门书",
                results.get(1).title()
        );
    }

    private void createTableAndInsertData() {
        executeSql("""
                CREATE TABLE products(
                    id BIGINT PRIMARY KEY,
                    title VARCHAR(200) NOT NULL,
                    description VARCHAR(500) NOT NULL,
                    price DECIMAL(10, 2) NOT NULL,
                    available BOOLEAN NOT NULL
                )
                """);

        executeSql("""
                INSERT INTO products(
                    id,
                    title,
                    description,
                    price,
                    available
                )
                VALUES
                    (
                        1,
                        'Java 入门书',
                        '适合零基础学习 Java',
                        59.90,
                        TRUE
                    ),
                    (
                        2,
                        'Spring Boot 实战书',
                        '学习 Spring Boot Web 开发',
                        89.90,
                        TRUE
                    ),
                    (
                        3,
                        '数据库基础书',
                        '学习关系型数据库基础',
                        39.90,
                        TRUE
                    )
                """);
    }

    private void insertUnavailableProduct() {
        executeSql("""
                INSERT INTO products(
                    id,
                    title,
                    description,
                    price,
                    available
                )
                VALUES(
                    4,
                    'Java 内部下架商品',
                    '不应对用户公开',
                    9.90,
                    FALSE
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
