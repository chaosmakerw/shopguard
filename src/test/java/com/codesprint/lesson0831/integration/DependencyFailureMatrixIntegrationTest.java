package com.codesprint.lesson0831.integration;

import com.codesprint.lesson0831.agent.AssistantService;
import com.codesprint.lesson0831.agent.ChatMessage;
import com.codesprint.lesson0831.agent.ChatModel;
import com.codesprint.lesson0831.agent.ChatRequest;
import com.codesprint.lesson0831.agent.ConversationKey;
import com.codesprint.lesson0831.agent.InMemoryConversationMemory;
import com.codesprint.lesson0831.agent.ModelCallException;
import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;
import com.codesprint.lesson0831.agent.toolcalling.ToolCallExecutor;
import com.codesprint.lesson0831.agent.toolcalling.ToolCallingRunner;
import com.codesprint.lesson0831.order.FakeOrderService;
import com.codesprint.lesson0831.order.OrderSummary;
import com.codesprint.lesson0831.order.OrderTools;
import com.codesprint.lesson0831.product.InMemoryProductService;
import com.codesprint.lesson0831.product.JdbcProductService;
import com.codesprint.lesson0831.product.ProductDetail;
import com.codesprint.lesson0831.product.ProductTools;
import com.codesprint.lesson0831.product.ToolArgumentConverter;
import com.codesprint.lesson0831.product.ToolArgumentValidator;
import com.codesprint.lesson0831.product.ToolDispatcher;
import com.codesprint.lesson0831.product.ToolResult;
import com.codesprint.lesson0831.rag.answer.RagAnswerCode;
import com.codesprint.lesson0831.rag.answer.RagAnswerGenerator;
import com.codesprint.lesson0831.rag.answer.RagAnswerResult;
import com.codesprint.lesson0831.rag.answer.RagAnswerService;
import com.codesprint.lesson0831.rag.answer.RagPromptBuilder;
import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.TextEmbeddingModel;
import com.codesprint.lesson0831.rag.retrieval.RetrievalRequest;
import com.codesprint.lesson0831.rag.retrieval.RetrievalService;
import com.codesprint.lesson0831.rag.retrieval.VectorSearchPort;
import com.codesprint.lesson0831.security.FakeCurrentUserContext;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DependencyFailureMatrixIntegrationTest {

    @Test
    void databaseFailureBecomesStableToolError() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(
                new SQLException(
                        "jdbc:mysql://private-db:3306 password=secret"
                )
        );

        ToolDispatcher dispatcher = dispatcher(
                new ProductTools(
                        new JdbcProductService(dataSource)
                ),
                new FakeOrderService()
        );

        ToolResult<?> result = dispatcher.dispatch(
                "search_products",
                Map.of(
                        "keyword", "Java",
                        "page", 1,
                        "size", 10,
                        "sort", "title_asc"
                )
        );

        assertFalse(result.success());
        assertEquals("INTERNAL_ERROR", result.code());
        assertEquals(
                "工具执行失败，请稍后重试",
                result.message()
        );
        assertFalse(result.message().contains("private-db"));
        assertFalse(result.message().contains("secret"));
    }

    @Test
    void vectorFailureStopsBeforeGeneration() {
        TextEmbeddingModel embedding =
                text -> List.of(1.0, 0.0);
        VectorSearchPort vectorSearch =
                (vector, version, visibilities, topK) -> {
                    throw new IllegalStateException(
                            "qdrant://private-vector:6333 api-key=secret"
                    );
                };
        RagAnswerGenerator generator =
                mock(RagAnswerGenerator.class);
        RagAnswerService service = new RagAnswerService(
                new RetrievalService(
                        embedding,
                        vectorSearch
                ),
                new RagPromptBuilder(),
                generator
        );

        RagAnswerResult result = service.answer(
                new RetrievalRequest(
                        "退款规则",
                        "v1",
                        Set.of(DocumentVisibility.PUBLIC),
                        3,
                        0.7
                )
        );

        assertFalse(result.success());
        assertEquals(
                RagAnswerCode.RETRIEVAL_UNAVAILABLE,
                result.code()
        );
        assertEquals("知识检索暂时不可用。", result.answer());
        assertTrue(result.citations().isEmpty());
        verifyNoInteractions(generator);
    }

    @Test
    void modelFailureAfterReadOnlyToolsDoesNotPolluteState() {
        InMemoryProductService productService =
                new InMemoryProductService();
        FakeOrderService orderService =
                new FakeOrderService();
        InMemoryConversationMemory memory =
                new InMemoryConversationMemory();

        ProductDetail productBefore =
                productService.getDetail(1L);
        List<OrderSummary> ordersBefore =
                orderService.listMyOrders(1001L);

        AtomicInteger modelCalls = new AtomicInteger();
        ChatModel model = failingAfterToolResults(modelCalls);
        ToolCallingRunner runner = new ToolCallingRunner(
                model,
                new ToolCallExecutor(
                        dispatcher(
                                new ProductTools(productService),
                                orderService
                        )
                ),
                3,
                Duration.ofSeconds(3)
        );
        AssistantService assistantService =
                new AssistantService(runner, memory);

        assertThrows(
                ModelCallException.class,
                () -> assistantService.ask(
                        new ChatRequest(
                                "1001",
                                "failure-session",
                                "读取商品和订单后模型失败"
                        )
                )
        );

        assertEquals(2, modelCalls.get());
        assertEquals(
                productBefore,
                productService.getDetail(1L)
        );
        assertEquals(
                ordersBefore,
                orderService.listMyOrders(1001L)
        );
        assertTrue(memory.load(
                new ConversationKey(
                        "1001",
                        "failure-session"
                )
        ).isEmpty());
    }

    private ChatModel failingAfterToolResults(
            AtomicInteger modelCalls
    ) {
        return new ChatModel() {
            @Override
            public String chat(
                    ChatRequest request,
                    List<ChatMessage> history
            ) {
                throw new AssertionError(
                        "不应该调用旧的chat方法"
                );
            }

            @Override
            public ModelTurn nextTurn(ModelInput input) {
                int call = modelCalls.incrementAndGet();

                if (call == 1) {
                    return new ModelTurn(
                            null,
                            List.of(
                                    new ToolCall(
                                            "product-call",
                                            "get_product_detail",
                                            Map.of("productId", 1)
                                    ),
                                    new ToolCall(
                                            "order-call",
                                            "list_my_orders",
                                            Map.of()
                                    )
                            )
                    );
                }

                assertEquals(1, input.exchanges().size());
                assertEquals(
                        2,
                        input.exchanges()
                                .get(0)
                                .results()
                                .size()
                );
                throw new ModelCallException("模拟模型故障");
            }
        };
    }

    private ToolDispatcher dispatcher(
            ProductTools productTools,
            FakeOrderService orderService
    ) {
        return new ToolDispatcher(
                new ToolArgumentValidator(),
                new ToolArgumentConverter(),
                productTools,
                new OrderTools(
                        new FakeCurrentUserContext(1001L),
                        orderService
                )
        );
    }
}
