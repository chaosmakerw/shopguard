package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.config.ModelProperties;
import com.codesprint.lesson0831.order.OrderSummary;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;
import com.codesprint.lesson0831.agent.toolcalling.ToolExchange;
import com.codesprint.lesson0831.agent.toolcalling.ToolExecutionResult;
import com.codesprint.lesson0831.product.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import java.net.SocketTimeoutException;
import java.util.List;
import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
class ProdChatModelTest {
    @Test
    void should_send_history_in_order_and_current_question_once() {
        ModelProperties properties = new ModelProperties();
        properties.setBaseUrl("http://model.test");
        properties.setApiKey("test-key");
        properties.setName("demo-model");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ProdChatModel model = new ProdChatModel(properties, builder.build());
        List<ChatMessage> history = List.of(
                new ChatMessage("system", "如实回答", 0),
                new ChatMessage("user", "我叫小王", 1),
                new ChatMessage("assistant", "你好，小王", 2)
        );
        server.expect(requestTo("http://model.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "model": "demo-model",
                          "messages": [
                            {"role":"system","content":"如实回答"},
                            {"role":"user","content":"我叫小王"},
                            {"role":"assistant","content":"你好，小王"},
                            {"role":"user","content":"我叫什么"}
                          ]
                        }
                        """, true))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"小王"}}]}
                        """, MediaType.APPLICATION_JSON));
        assertEquals("小王", model.chat(new ChatRequest("a", "shared", "我叫什么"), history));
        assertEquals(3, history.size());
        server.verify();
    }
    @Test
    void timeout_should_be_mapped_to_model_call_exception() {
        ModelProperties properties = new ModelProperties();
        properties.setBaseUrl("http://model.test");
        properties.setApiKey("test-key");
        properties.setName("demo-model");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl());

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        ProdChatModel model =
                new ProdChatModel(properties, builder.build());

        server.expect(requestTo(
                        "http://model.test/v1/chat/completions"
                ))
                .andRespond(request -> {
                    throw new ResourceAccessException(
                            "read timed out",
                            new SocketTimeoutException("read timed out")
                    );
                });

        ModelCallException exception = assertThrows(
                ModelCallException.class,
                () -> model.chat(new ChatRequest(
                        "user-a", "session-1", "你好"
                ), List.of())
        );

        assertEquals(
                "模型服务调用失败",
                exception.getMessage()
        );

        server.verify();
    }

    @Test
    void should_send_post_request_and_parse_answer() {
        ModelProperties properties = new ModelProperties();
        properties.setBaseUrl("http://model.test");
        properties.setApiKey("test-key");
        properties.setName("demo-model");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl());

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        ProdChatModel model =
                new ProdChatModel(properties, builder.build());

        server.expect(requestTo(
                        "http://model.test/v1/chat/completions"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "Authorization",
                        "Bearer test-key"
                ))
                .andExpect(content().json("""
                        {
                          "model": "demo-model",
                          "messages": [
                            {
                              "role": "user",
                              "content": "什么是 Spring Bean？"
                            }
                          ]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "Spring Bean 是由 Spring 容器管理的对象。"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        String answer = model.chat(
                new ChatRequest(
                        "user-a", "session-1", "什么是 Spring Bean？"
                ), List.of()
        );

        assertEquals(
                "Spring Bean 是由 Spring 容器管理的对象。",
                answer
        );

        server.verify();
    }
    @Test
    void empty_choices_should_throw_model_call_exception() {
        ModelProperties properties = new ModelProperties();
        properties.setBaseUrl("http://model.test");
        properties.setApiKey("test-key");
        properties.setName("demo-model");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl());

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        ProdChatModel model =
                new ProdChatModel(properties, builder.build());

        server.expect(requestTo(
                        "http://model.test/v1/chat/completions"
                ))
                .andRespond(withSuccess(
                        """
                        {
                          "choices": []
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        ModelCallException exception = assertThrows(
                ModelCallException.class,
                () -> model.chat(new ChatRequest(
                        "user-a", "session-1", "你好"
                ), List.of())
        );

        assertEquals(
                "模型返回了空结果",
                exception.getMessage()
        );

        server.verify();
    }
    @Test
    void rate_limit_should_be_mapped_to_model_call_exception() {
        ModelProperties properties = new ModelProperties();
        properties.setBaseUrl("http://model.test");
        properties.setApiKey("test-key");
        properties.setName("demo-model");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl());

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        ProdChatModel model =
                new ProdChatModel(properties, builder.build());

        server.expect(requestTo(
                        "http://model.test/v1/chat/completions"
                ))
                .andRespond(withStatus(
                        HttpStatus.TOO_MANY_REQUESTS
                ));

        ModelCallException exception = assertThrows(
                ModelCallException.class,
                () -> model.chat(new ChatRequest(
                        "user-a", "session-1", "你好"
                ), List.of())
        );

        assertEquals(
                "模型服务调用失败",
                exception.getMessage()
        );

        server.verify();
    }
    @Test
    void shouldConvertToolMessageToModelTurn() {
        ProdChatModel model = new ProdChatModel(
                new ModelProperties(),
                RestClient.builder().build()
        );

        ModelMessage message = new ModelMessage(
                "assistant",
                null,
                List.of(new ModelMessage.ToolCallPayload(
                        "call-001",
                        "function",
                        new ModelMessage.FunctionPayload(
                                "list_my_orders", "{}"
                        )
                )),
                null
        );

        ModelTurn turn = model.toModelTurn(message);

        assertTrue(turn.hasToolCalls());
        assertEquals(1, turn.toolCalls().size());
        assertEquals("call-001", turn.toolCalls().get(0).id());
        assertEquals("list_my_orders", turn.toolCalls().get(0).name());
        assertEquals(Map.of(), turn.toolCalls().get(0).arguments());
    }
    @Test
    void shouldConvertTextMessageToModelTurn() {
        ProdChatModel model = new ProdChatModel(
                new ModelProperties(),
                RestClient.builder().build()
        );

        ModelMessage message =
                new ModelMessage("assistant", "你好");

        ModelTurn turn = model.toModelTurn(message);

        assertEquals("你好", turn.text());
        assertFalse(turn.hasToolCalls());
        assertTrue(turn.toolCalls().isEmpty());
    }
    @Test
    void shouldRejectArrayArguments() {
        ProdChatModel model = new ProdChatModel(
                new ModelProperties(),
                RestClient.builder().build()
        );

        ModelMessage message = new ModelMessage(
                "assistant",
                null,
                List.of(new ModelMessage.ToolCallPayload(
                        "call-001",
                        "function",
                        new ModelMessage.FunctionPayload(
                                "list_my_orders", "[]"
                        )
                )),
                null
        );

        ModelCallException exception = assertThrows(
                ModelCallException.class,
                () -> model.toModelTurn(message)
        );

        assertEquals("模型工具参数格式错误", exception.getMessage());
    }
    @Test
    void shouldSendToolsAndReadToolCall() {
        ModelProperties properties = new ModelProperties();
        properties.setBaseUrl("http://model.test");
        properties.setApiKey("test-key");
        properties.setName("demo-model");

        RestClient.Builder builder =
                RestClient.builder().baseUrl(properties.getBaseUrl());

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        ProdChatModel model =
                new ProdChatModel(properties, builder.build());

        server.expect(requestTo("http://model.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("demo-model"))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content")
                        .value("我的订单有哪些"))
                .andExpect(jsonPath("$.tools.length()").value(4))
                .andExpect(jsonPath("$.tools[2].function.name")
                        .value("list_my_orders"))
                .andRespond(withSuccess("""
                    {
                      "choices": [{
                        "message": {
                          "role": "assistant",
                          "content": null,
                          "tool_calls": [{
                            "id": "call-001",
                            "type": "function",
                            "function": {
                              "name": "list_my_orders",
                              "arguments": "{}"
                            }
                          }]
                        }
                      }]
                    }
                    """, MediaType.APPLICATION_JSON));

        ModelTurn turn = model.nextTurn(
                new ModelInput(
                        new ChatRequest(
                                "user-a", "session-1", "我的订单有哪些"
                        ),
                        List.of(),
                        List.of()
                )
        );

        assertTrue(turn.hasToolCalls());
        assertEquals(1, turn.toolCalls().size());
        assertEquals("call-001", turn.toolCalls().get(0).id());
        assertEquals("list_my_orders", turn.toolCalls().get(0).name());
        assertEquals(Map.of(), turn.toolCalls().get(0).arguments());

        server.verify();
    }
    @Test
    void shouldSendToolResultAndReadFinalAnswer() throws Exception {
        ModelProperties properties = new ModelProperties();
        properties.setBaseUrl("http://model.test");
        properties.setApiKey("test-key");
        properties.setName("demo-model");

        RestClient.Builder builder =
                RestClient.builder().baseUrl(properties.getBaseUrl());

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        ProdChatModel model =
                new ProdChatModel(properties, builder.build());

        ToolCall call = new ToolCall(
                "call-001", "list_my_orders", Map.of()
        );

        ToolExchange exchange = new ToolExchange(
                new ModelTurn(null, List.of(call)),
                List.of(new ToolExecutionResult(
                        "call-001", ToolResult.success(List.of())
                ))
        );

        ModelInput input = new ModelInput(
                new ChatRequest("user-a", "session-1", "我的订单有哪些"),
                List.of(),
                List.of(exchange)
        );

        ObjectMapper mapper = new ObjectMapper();

        server.expect(requestTo("http://model.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.tools.length()").value(4))
                .andExpect(jsonPath("$.messages.length()").value(3))

                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content")
                        .value("我的订单有哪些"))

                .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.messages[1].tool_calls", hasSize(1)))
                .andExpect(jsonPath("$.messages[1].tool_calls[0].id")
                        .value("call-001"))
                .andExpect(jsonPath("$.messages[1].tool_calls[0].function.name")
                        .value("list_my_orders"))
                .andExpect(jsonPath("$.messages[1].tool_calls[0].function.arguments")
                        .value("{}"))

                .andExpect(jsonPath("$.messages[2].role").value("tool"))
                .andExpect(jsonPath("$.messages[2].tool_call_id")
                        .value("call-001"))
                .andExpect(jsonPath("$.messages[2].content").value(
                        mapper.writeValueAsString(ToolResult.success(List.of()))
                ))

                .andRespond(withSuccess("""
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "你目前没有订单"
                    }
                  }]
                }
                """, MediaType.APPLICATION_JSON));
        ModelTurn turn = model.nextTurn(input);

        assertEquals("你目前没有订单", turn.text());
        assertFalse(turn.hasToolCalls());

        server.verify();
    }
    @Test
    void nextTurn_should_send_tool_result_back_and_receive_final_answer() {
        // 1. 准备 ProdChatModel 的测试配置
        ModelProperties properties = new ModelProperties();
        properties.setBaseUrl("http://localhost");
        properties.setApiKey("test-api-key");
        properties.setName("test-model");

        RestClient.Builder builder = RestClient.builder();

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        RestClient restClient = builder
                .baseUrl(properties.getBaseUrl())
                .build();

        ProdChatModel model =
                new ProdChatModel(properties, restClient);


        // 2. 当前用户问题
        ChatRequest request = new ChatRequest(
                "user-a",
                "session-1",
                "查询我的订单"
        );


        // 3. 模拟模型上一轮产生的工具调用
        ToolCall toolCall = new ToolCall(
                "call-001",
                "list_my_orders",
                Map.of()
        );

        ModelTurn toolTurn = new ModelTurn(
                null,
                List.of(toolCall)
        );


        // 4. 模拟 Java 已经执行完工具
        ToolExecutionResult toolResult =
                new ToolExecutionResult(
                        "call-001",
                        ToolResult.success(
                                List.of(
                                        new OrderSummary(
                                                1L,
                                                1001L,
                                                "Java编程思想",
                                                "PAID"
                                        )
                                )
                        )
                );


        // 5. 把“模型工具调用 + Java 工具结果”组成一次 ToolExchange
        ToolExchange exchange = new ToolExchange(
                toolTurn,
                List.of(toolResult)
        );


        // 6. 构造第二次模型调用需要的 ModelInput
        ModelInput input = new ModelInput(
                request,
                List.of(),
                List.of(exchange)
        );


        // 7. 验证 ProdChatModel 真正发送给模型的第二次 HTTP 请求
        server.expect(requestTo(
                        "http://localhost/v1/chat/completions"
                ))
                .andExpect(method(HttpMethod.POST))

                // 当前用户问题
                .andExpect(
                        jsonPath("$.messages[0].role")
                                .value("user")
                )
                .andExpect(
                        jsonPath("$.messages[0].content")
                                .value("查询我的订单")
                )

                // 模型上一轮的 tool call 必须重新发送
                .andExpect(
                        jsonPath("$.messages[1].role")
                                .value("assistant")
                )
                .andExpect(
                        jsonPath(
                                "$.messages[1].tool_calls[0].id"
                        ).value("call-001")
                )
                .andExpect(
                        jsonPath(
                                "$.messages[1].tool_calls[0].type"
                        ).value("function")
                )
                .andExpect(
                        jsonPath(
                                "$.messages[1].tool_calls[0].function.name"
                        ).value("list_my_orders")
                )

                // Java 工具结果必须作为 tool message 回传
                .andExpect(
                        jsonPath("$.messages[2].role")
                                .value("tool")
                )
                .andExpect(
                        jsonPath("$.messages[2].tool_call_id")
                                .value("call-001")
                )

                // content 在模型协议中是字符串
                .andExpect(
                        jsonPath("$.messages[2].content")
                                .isString()
                )

                // 并且字符串里确实有 Java 工具执行得到的数据
                .andExpect(
                        jsonPath("$.messages[2].content")
                                .value(
                                        containsString(
                                                "Java编程思想"
                                        )
                                )
                )

                // 8. 模拟模型看到工具结果之后给出最终答案
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "choices": [
                                    {
                                      "message": {
                                        "role": "assistant",
                                        "content": "你目前有一笔订单：Java编程思想。"
                                      }
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );


        // 9. 真正执行生产代码
        ModelTurn finalTurn = model.nextTurn(input);


        // 10. 验证模型已经返回最终自然语言结果
        assertEquals(
                "你目前有一笔订单：Java编程思想。",
                finalTurn.text()
        );

        assertFalse(finalTurn.hasToolCalls());

        // 11. 确认上面的 Mock 请求确实发生
        server.verify();
    }
}
