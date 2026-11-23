package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.agent.toolcalling.ToolCallExecutor;
import com.codesprint.lesson0831.agent.toolcalling.ToolCallingRunner;
import com.codesprint.lesson0831.config.ModelProperties;
import com.codesprint.lesson0831.observability.AgentOperationMetrics;
import com.codesprint.lesson0831.order.FakeOrderService;
import com.codesprint.lesson0831.order.OrderTools;
import com.codesprint.lesson0831.product.InMemoryProductService;
import com.codesprint.lesson0831.product.ProductTools;
import com.codesprint.lesson0831.product.ToolArgumentConverter;
import com.codesprint.lesson0831.product.ToolArgumentValidator;
import com.codesprint.lesson0831.product.ToolDispatcher;
import com.codesprint.lesson0831.security.FakeCurrentUserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class ProdToolCallingIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void shouldCompleteToolCallingThroughRealHttpAdapter(
            CapturedOutput capturedOutput
    )
            throws Exception {
        AtomicInteger requestCount =
                new AtomicInteger();

        AtomicReference<JsonNode> firstRequest =
                new AtomicReference<>();

        AtomicReference<JsonNode> secondRequest =
                new AtomicReference<>();

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    JsonNode requestBody =
                            OBJECT_MAPPER.readTree(
                                    exchange.getRequestBody()
                            );

                    int currentRequest =
                            requestCount.incrementAndGet();

                    if (currentRequest == 1) {
                        firstRequest.set(requestBody);

                        sendJson(
                                exchange,
                                """
                                {
                                  "choices": [
                                    {
                                      "message": {
                                        "role": "assistant",
                                        "tool_calls": [
                                          {
                                            "id": "call-001",
                                            "type": "function",
                                            "function": {
                                              "name": "get_product_detail",
                                              "arguments": "{\\"productId\\":1}"
                                            }
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """
                        );

                        return;
                    }

                    secondRequest.set(requestBody);

                    sendJson(
                            exchange,
                            """
                            {
                              "choices": [
                                {
                                  "message": {
                                    "role": "assistant",
                                    "content": "商品是 Java 入门书，价格 59.90 元"
                                  }
                                }
                              ]
                            }
                            """
                    );
                }
        );

        server.start();
        MDC.put("requestId", "req-trace-001");

        try {
            ModelProperties properties =
                    new ModelProperties();

            properties.setBaseUrl(
                    "http://127.0.0.1:"
                            + server.getAddress().getPort()
            );
            properties.setApiKey("test-key");
            properties.setName("demo-model");
            properties.setTimeout(Duration.ofSeconds(1));

            SimpleMeterRegistry registry =
                    new SimpleMeterRegistry();
            AgentOperationMetrics metrics =
                    new AgentOperationMetrics(registry);

            ProdChatModel model =
                    new ProdChatModel(properties, metrics);

            ProductTools productTools =
                    new ProductTools(
                            new InMemoryProductService()
                    );

            OrderTools orderTools =
                    new OrderTools(
                            new FakeCurrentUserContext(1001L),
                            new FakeOrderService()
                    );

            ToolDispatcher dispatcher =
                    new ToolDispatcher(
                            new ToolArgumentValidator(),
                            new ToolArgumentConverter(),
                            productTools,
                            orderTools,
                            metrics
                    );

            ToolCallingRunner runner =
                    new ToolCallingRunner(
                            model,
                            new ToolCallExecutor(dispatcher),
                            3,
                            Duration.ofSeconds(3)
                    );

            String answer = runner.run(
                    new ChatRequest(
                            "user-a",
                            "session-1",
                            "查看一号商品详情"
                    ),
                    List.of()
            );

            assertEquals(
                    "商品是 Java 入门书，价格 59.90 元",
                    answer
            );

            assertEquals(2, requestCount.get());

            JsonNode first = firstRequest.get();
            JsonNode second = secondRequest.get();

            assertNotNull(first);
            assertNotNull(second);
            assertEquals(4, first.path("tools").size());

            JsonNode messages =
                    second.path("messages");

            assertEquals(3, messages.size());
            assertEquals(
                    "assistant",
                    messages.get(1).path("role").asText()
            );
            assertEquals(
                    "tool",
                    messages.get(2).path("role").asText()
            );
            assertEquals(
                    "call-001",
                    messages.get(2)
                            .path("tool_call_id")
                            .asText()
            );

            JsonNode toolResult =
                    OBJECT_MAPPER.readTree(
                            messages.get(2)
                                    .path("content")
                                    .asText()
                    );

            assertTrue(
                    toolResult.path("success").asBoolean()
            );
            assertEquals(
                    "OK",
                    toolResult.path("code").asText()
            );
            assertEquals(
                    "Java 入门书",
                    toolResult.path("data")
                            .path("title")
                            .asText()
            );

            String logs = capturedOutput.getAll();
            assertEquals(
                    3,
                    countOccurrences(
                            logs,
                            "requestId=req-trace-001"
                    )
            );
            assertTrue(logs.contains("toolName=get_product_detail"));
            assertTrue(logs.contains("resultCode=OK"));
            assertTrue(logs.contains("resultCode=SUCCESS"));
            assertTrue(!logs.contains("test-key"));
            assertTrue(!logs.contains("查看一号商品详情"));

            assertTimerCount(
                    registry,
                    "model",
                    "SUCCESS",
                    2L
            );
            assertTimerCount(
                    registry,
                    "tool",
                    "OK",
                    1L
            );
        } finally {
            MDC.remove("requestId");
            server.stop(0);
        }
    }

    private void assertTimerCount(
            SimpleMeterRegistry registry,
            String stage,
            String resultCode,
            long expectedCount
    ) {
        Timer timer = registry.find(
                        AgentOperationMetrics.METRIC_NAME
                )
                .tag("stage", stage)
                .tag("resultCode", resultCode)
                .timer();

        assertNotNull(timer);
        assertEquals(expectedCount, timer.count());
    }

    private int countOccurrences(
            String text,
            String marker
    ) {
        int count = 0;
        int fromIndex = 0;

        while (true) {
            int found = text.indexOf(marker, fromIndex);

            if (found < 0) {
                return count;
            }

            count++;
            fromIndex = found + marker.length();
        }
    }

    private static void sendJson(
            HttpExchange exchange,
            String json
    ) throws IOException {
        byte[] body =
                json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json"
        );

        exchange.sendResponseHeaders(
                200,
                body.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
