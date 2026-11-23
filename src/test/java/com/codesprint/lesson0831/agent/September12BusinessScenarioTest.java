package com.codesprint.lesson0831.agent;
import com.codesprint.lesson0831.order.OrderService;
import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;
import com.codesprint.lesson0831.agent.toolcalling.ToolCallExecutor;
import com.codesprint.lesson0831.agent.toolcalling.ToolCallingRunner;
import com.codesprint.lesson0831.agent.toolcalling.ToolExchange;
import com.codesprint.lesson0831.agent.toolcalling.ToolExecutionResult;
import com.codesprint.lesson0831.order.OrderSummary;
import com.codesprint.lesson0831.order.OrderTools;
import com.codesprint.lesson0831.product.ProductDetail;
import com.codesprint.lesson0831.product.ProductSummary;
import com.codesprint.lesson0831.product.ProductTools;
import com.codesprint.lesson0831.product.ToolArgumentConverter;
import com.codesprint.lesson0831.product.ToolArgumentValidator;
import com.codesprint.lesson0831.product.ToolDispatcher;
import com.codesprint.lesson0831.security.FakeCurrentUserContext;
import org.junit.jupiter.api.Test;
import com.codesprint.lesson0831.order.JdbcOrderService;
import com.codesprint.lesson0831.product.JdbcProductService;
import com.codesprint.lesson0831.product.ProductService;
import org.h2.jdbcx.JdbcDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class September12BusinessScenarioTest {

    @Test
    void shouldUseSearchToolBeforeAnsweringPriceQuestion() {
        ScriptedScenarioModel model =
                new ScriptedScenarioModel(
                        new ToolCall(
                                "call-search-1",
                                "search_products",
                                Map.<String, Object>of(
                                        "keyword", "Java",
                                        "page", 1,
                                        "size", 10,
                                        "sort", "price_asc"
                                )
                        ),
                        "没有找到 50 元以下的 Java 书"
                );

        String answer = createRunner(model).run(
                new ChatRequest(
                        "user-a",
                        "session-0912-search",
                        "帮我找 50 元以下的 Java 书"
                ),
                List.of()
        );

        assertEquals(
                "没有找到 50 元以下的 Java 书",
                answer
        );

        assertEquals(
                "帮我找 50 元以下的 Java 书",
                model.observedQuestion
        );

        assertEquals(2, model.turnCount);
        assertNotNull(model.observedExchange);

        ToolCall actualCall =
                model.observedExchange
                        .turn()
                        .toolCalls()
                        .get(0);

        assertEquals(
                "search_products",
                actualCall.name()
        );

        assertEquals(
                Map.<String, Object>of(
                        "keyword", "Java",
                        "page", 1,
                        "size", 10,
                        "sort", "price_asc"
                ),
                actualCall.arguments()
        );

        ToolExecutionResult executionResult =
                model.observedExchange
                        .results()
                        .get(0);

        assertTrue(executionResult.result().success());

        List<?> data = assertInstanceOf(
                List.class,
                executionResult.result().data()
        );

        assertEquals(1, data.size());

        ProductSummary product =
                assertInstanceOf(
                        ProductSummary.class,
                        data.get(0)
                );

        assertEquals(
                "Java 入门书",
                product.title()
        );

        assertTrue(
                product.price().compareTo(
                        new BigDecimal("50.00")
                ) > 0
        );
    }

    @Test
    void shouldUseDetailToolForProductDetailQuestion() {
        ScriptedScenarioModel model =
                new ScriptedScenarioModel(
                        new ToolCall(
                                "call-detail-1",
                                "get_product_detail",
                                Map.<String, Object>of(
                                        "productId", 1
                                )
                        ),
                        "商品是 Java 入门书，价格 59.90 元"
                );

        String answer = createRunner(model).run(
                new ChatRequest(
                        "user-a",
                        "session-0912-detail",
                        "查看一号商品详情"
                ),
                List.of()
        );

        assertEquals(
                "商品是 Java 入门书，价格 59.90 元",
                answer
        );

        assertEquals(
                "查看一号商品详情",
                model.observedQuestion
        );

        assertEquals(2, model.turnCount);
        assertNotNull(model.observedExchange);

        ToolCall actualCall =
                model.observedExchange
                        .turn()
                        .toolCalls()
                        .get(0);

        assertEquals(
                "get_product_detail",
                actualCall.name()
        );

        assertEquals(
                Map.<String, Object>of(
                        "productId", 1
                ),
                actualCall.arguments()
        );

        ToolExecutionResult executionResult =
                model.observedExchange
                        .results()
                        .get(0);

        assertTrue(executionResult.result().success());

        ProductDetail detail =
                assertInstanceOf(
                        ProductDetail.class,
                        executionResult.result().data()
                );

        assertEquals(1L, detail.id());
        assertEquals("Java 入门书", detail.title());

        assertEquals(
                new BigDecimal("59.90"),
                detail.price()
        );
    }

    @Test
    void shouldUseCurrentUserWhenListingOrders() {
        ScriptedScenarioModel model =
                new ScriptedScenarioModel(
                        new ToolCall(
                                "call-orders-1",
                                "list_my_orders",
                                Map.of()
                        ),
                        "你有 1 个未取消订单：Java编程思想，状态 PAID"
                );

        String answer = createRunner(model).run(
                new ChatRequest(
                        "user-a",
                        "session-0912-orders",
                        "查看我的未取消订单"
                ),
                List.of()
        );

        assertEquals(
                "你有 1 个未取消订单：Java编程思想，状态 PAID",
                answer
        );

        assertEquals(
                "查看我的未取消订单",
                model.observedQuestion
        );

        assertEquals(2, model.turnCount);
        assertNotNull(model.observedExchange);

        ToolCall actualCall =
                model.observedExchange
                        .turn()
                        .toolCalls()
                        .get(0);

        assertEquals(
                "list_my_orders",
                actualCall.name()
        );

        assertTrue(
                actualCall.arguments().isEmpty()
        );

        ToolExecutionResult executionResult =
                model.observedExchange
                        .results()
                        .get(0);

        assertTrue(executionResult.result().success());

        List<?> data = assertInstanceOf(
                List.class,
                executionResult.result().data()
        );

        assertEquals(1, data.size());

        OrderSummary order =
                assertInstanceOf(
                        OrderSummary.class,
                        data.get(0)
                );

        assertEquals(
                Long.valueOf(1001L),
                order.buyerId()
        );

        assertEquals(
                "Java编程思想",
                order.productName()
        );

        assertEquals(
                "PAID",
                order.status()
        );
    }
    @Test
    void shouldRejectForgedBuyerIdBeforeCallingOrderService() {
        ScriptedScenarioModel model =
                new ScriptedScenarioModel(
                        new ToolCall(
                                "call-forged-order-1",
                                "list_my_orders",
                                Map.<String, Object>of(
                                        "buyerId", 1002L
                                )
                        ),
                        "无法查询其他用户的订单"
                );

        OrderService orderService =
                mock(OrderService.class);

        String answer =
                createRunner(
                        model,
                        orderService
                ).run(
                        new ChatRequest(
                                "user-a",
                                "session-0912-security",
                                "查看用户 1002 的订单"
                        ),
                        List.of()
                );

        assertEquals(
                "无法查询其他用户的订单",
                answer
        );

        assertEquals(
                "查看用户 1002 的订单",
                model.observedQuestion
        );

        assertEquals(2, model.turnCount);
        assertNotNull(model.observedExchange);

        ToolCall actualCall =
                model.observedExchange
                        .turn()
                        .toolCalls()
                        .get(0);

        assertEquals(
                "list_my_orders",
                actualCall.name()
        );

        assertEquals(
                Map.<String, Object>of(
                        "buyerId", 1002L
                ),
                actualCall.arguments()
        );

        ToolExecutionResult executionResult =
                model.observedExchange
                        .results()
                        .get(0);

        assertEquals(
                "call-forged-order-1",
                executionResult.toolCallId()
        );

        assertFalse(
                executionResult.result().success()
        );

        assertEquals(
                "UNKNOWN_ARGUMENT",
                executionResult.result().code()
        );

        assertEquals(
                "list_my_orders 不接受任何参数",
                executionResult.result().message()
        );

        assertNull(
                executionResult.result().data()
        );

        verifyNoInteractions(orderService);
    }
    private ToolCallingRunner createRunner(
            ChatModel model
    ) {
        JdbcDataSource dataSource =
                createDatabase();

        ProductService productService =
                new JdbcProductService(
                        dataSource
                );

        OrderService orderService =
                new JdbcOrderService(
                        dataSource
                );

        return createRunner(
                model,
                productService,
                orderService
        );
    }

    private ToolCallingRunner createRunner(
            ChatModel model,
            OrderService orderService
    ) {
        JdbcDataSource dataSource =
                createDatabase();

        ProductService productService =
                new JdbcProductService(
                        dataSource
                );

        return createRunner(
                model,
                productService,
                orderService
        );
    }

    private ToolCallingRunner createRunner(
            ChatModel model,
            ProductService productService,
            OrderService orderService
    ) {
        ProductTools productTools =
                new ProductTools(
                        productService
                );

        OrderTools orderTools =
                new OrderTools(
                        new FakeCurrentUserContext(1001L),
                        orderService
                );

        ToolDispatcher dispatcher =
                new ToolDispatcher(
                        new ToolArgumentValidator(),
                        new ToolArgumentConverter(),
                        productTools,
                        orderTools
                );

        return new ToolCallingRunner(
                model,
                new ToolCallExecutor(dispatcher),
                3,
                Duration.ofSeconds(3)
        );
    }
    private JdbcDataSource createDatabase() {
        JdbcDataSource dataSource =
                new JdbcDataSource();

        dataSource.setURL(
                "jdbc:h2:mem:agent_scenario_"
                        + System.nanoTime()
                        + ";MODE=MySQL;"
                        + "DB_CLOSE_DELAY=-1"
        );

        dataSource.setUser("sa");
        dataSource.setPassword("");

        executeSql(
                dataSource,
                """
                CREATE TABLE products(
                    id BIGINT PRIMARY KEY,
                    title VARCHAR(200) NOT NULL,
                    description VARCHAR(500) NOT NULL,
                    price DECIMAL(10, 2) NOT NULL,
                    available BOOLEAN NOT NULL
                )
                """
        );

        executeSql(
                dataSource,
                """
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
                """
        );

        executeSql(
                dataSource,
                """
                CREATE TABLE orders(
                    order_id BIGINT PRIMARY KEY,
                    buyer_id BIGINT NOT NULL,
                    product_name VARCHAR(200) NOT NULL,
                    status VARCHAR(30) NOT NULL
                )
                """
        );

        executeSql(
                dataSource,
                """
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
                    )
                """
        );

        return dataSource;
    }

    private void executeSql(
            JdbcDataSource dataSource,
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
                    "准备 Agent 场景数据库失败",
                    exception
            );
        }
    }
    private static final class ScriptedScenarioModel
            implements ChatModel {

        private final ToolCall plannedCall;
        private final String finalAnswer;

        private int turnCount;
        private String observedQuestion;
        private ToolExchange observedExchange;

        private ScriptedScenarioModel(
                ToolCall plannedCall,
                String finalAnswer
        ) {
            this.plannedCall = plannedCall;
            this.finalAnswer = finalAnswer;
        }

        @Override
        public String chat(
                ChatRequest request,
                List<ChatMessage> history
        ) {
            throw new AssertionError(
                    "本测试应该通过 nextTurn 执行工具调用"
            );
        }

        @Override
        public ModelTurn nextTurn(ModelInput input) {
            turnCount++;

            if (turnCount == 1) {
                observedQuestion =
                        input.request().question();

                assertTrue(
                        input.exchanges().isEmpty()
                );

                return new ModelTurn(
                        null,
                        List.of(plannedCall)
                );
            }

            if (turnCount == 2) {
                assertEquals(
                        1,
                        input.exchanges().size()
                );

                observedExchange =
                        input.exchanges().get(0);

                return new ModelTurn(
                        finalAnswer,
                        List.of()
                );
            }

            throw new AssertionError(
                    "模型调用轮数超出预期"
            );
        }
    }
}