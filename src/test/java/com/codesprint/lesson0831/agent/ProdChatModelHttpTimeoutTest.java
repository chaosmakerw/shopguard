package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.config.ModelProperties;
import com.codesprint.lesson0831.observability.AgentOperationMetrics;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClientException;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class ProdChatModelHttpTimeoutTest {

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void nextTurnShouldStopWhenHttpReadTimeoutExpires(
            CapturedOutput capturedOutput
    ) throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    try {
                        Thread.sleep(1000);

                        byte[] body = """
                                {
                                  "choices": [
                                    {
                                      "message": {
                                        "role": "assistant",
                                        "content": "迟到的回答"
                                      }
                                    }
                                  ]
                                }
                                """.getBytes(StandardCharsets.UTF_8);

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
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                }
        );

        server.start();
        MDC.put("requestId", "req-model-timeout");

        try {
            ModelProperties properties = new ModelProperties();
            properties.setBaseUrl(
                    "http://127.0.0.1:"
                            + server.getAddress().getPort()
            );
            properties.setApiKey("test-key");
            properties.setName("demo-model");
            properties.setTimeout(Duration.ofMillis(100));

            SimpleMeterRegistry registry =
                    new SimpleMeterRegistry();
            ProdChatModel model =
                    new ProdChatModel(
                            properties,
                            new AgentOperationMetrics(registry)
                    );

            ModelInput input = new ModelInput(
                    new ChatRequest(
                            "user-a",
                            "session-1",
                            "查询我的订单"
                    ),
                    List.of(),
                    List.of()
            );

            ModelCallException exception = assertThrows(
                    ModelCallException.class,
                    () -> model.nextTurn(input)
            );

            assertEquals(
                    "模型服务调用失败",
                    exception.getMessage()
            );

            assertInstanceOf(
                    RestClientException.class,
                    exception.getCause()
            );

            assertTrue(hasCause(
                    exception,
                    SocketTimeoutException.class
            ));

            String logs = capturedOutput.getAll();
            assertTrue(logs.contains("requestId=req-model-timeout"));
            assertTrue(logs.contains("resultCode=MODEL_UNAVAILABLE"));
            assertTrue(logs.contains("exceptionType="));
            assertTrue(logs.matches("(?s).*latencyMs=\\d+.*"));
            assertTrue(!logs.contains("test-key"));
            assertTrue(!logs.contains("查询我的订单"));

            Timer timer = registry.find(
                            AgentOperationMetrics.METRIC_NAME
                    )
                    .tag("stage", "model")
                    .tag(
                            "resultCode",
                            "MODEL_UNAVAILABLE"
                    )
                    .timer();

            assertNotNull(timer);
            assertEquals(1L, timer.count());
        } finally {
            MDC.remove("requestId");
            server.stop(0);
        }
    }

    private boolean hasCause(
            Throwable throwable,
            Class<? extends Throwable> expectedType
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}
