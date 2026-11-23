package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.config.ModelProperties;
import com.codesprint.lesson0831.observability.AgentOperationMetrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProdChatModelMetricsTest {

    @Test
    void shouldRecordOneSuccessForValidModelResponse() {
        TestContext context = context();
        context.server().expect(
                        requestTo(
                                "http://model.test/v1/chat/completions"
                        )
                )
                .andRespond(withSuccess("""
                        {
                          "choices": [{
                            "message": {
                              "role": "assistant",
                              "content": "正常回答"
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        ModelTurn turn = context.model().nextTurn(input());

        assertEquals("正常回答", turn.text());
        context.server().verify();
        assertTimerCount(context.registry(), "SUCCESS", 1L);
        assertNull(timer(
                context.registry(),
                "MODEL_UNAVAILABLE"
        ));
    }

    @Test
    void shouldRecordOneFailureForInvalidModelResponse() {
        TestContext context = context();
        context.server().expect(
                        requestTo(
                                "http://model.test/v1/chat/completions"
                        )
                )
                .andRespond(withSuccess("""
                        {
                          "choices": [{
                            "message": {
                              "role": "assistant",
                              "content": ""
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThrows(
                ModelCallException.class,
                () -> context.model().nextTurn(input())
        );

        context.server().verify();
        assertTimerCount(
                context.registry(),
                "MODEL_UNAVAILABLE",
                1L
        );
        assertNull(timer(context.registry(), "SUCCESS"));
    }

    private TestContext context() {
        ModelProperties properties = new ModelProperties();
        properties.setBaseUrl("http://model.test");
        properties.setApiKey("test-key");
        properties.setName("demo-model");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl());
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        SimpleMeterRegistry registry =
                new SimpleMeterRegistry();
        AgentOperationMetrics metrics =
                new AgentOperationMetrics(registry);
        ProdChatModel model = new ProdChatModel(
                properties,
                builder.build(),
                metrics
        );

        return new TestContext(model, server, registry);
    }

    private ModelInput input() {
        return new ModelInput(
                new ChatRequest(
                        "user-a",
                        "session-1",
                        "查询我的订单"
                ),
                List.of(),
                List.of()
        );
    }

    private void assertTimerCount(
            SimpleMeterRegistry registry,
            String resultCode,
            long expectedCount
    ) {
        Timer timer = timer(registry, resultCode);

        assertNotNull(timer);
        assertEquals(expectedCount, timer.count());
    }

    private Timer timer(
            SimpleMeterRegistry registry,
            String resultCode
    ) {
        return registry.find(
                        AgentOperationMetrics.METRIC_NAME
                )
                .tag("stage", "model")
                .tag("resultCode", resultCode)
                .timer();
    }

    private record TestContext(
            ProdChatModel model,
            MockRestServiceServer server,
            SimpleMeterRegistry registry
    ) {
    }
}
