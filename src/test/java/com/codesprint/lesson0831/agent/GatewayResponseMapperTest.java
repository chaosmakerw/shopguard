package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GatewayResponseMapperTest {
    private final GatewayResponseMapper mapper = new GatewayResponseMapper();

    @Test
    void returns_text_when_tool_calls_are_absent() {
        ModelTurn turn = mapper.toModelTurn(new ModelMessage("assistant", "商品价格为42元"));
        assertEquals("商品价格为42元", turn.text());
        assertFalse(turn.hasToolCalls());
    }

    @Test
    void returns_text_when_tool_calls_are_empty() {
        ModelTurn turn = mapper.toModelTurn(
                new ModelMessage("assistant", "最终回答", List.of(), null));
        assertEquals("最终回答", turn.text());
        assertTrue(turn.toolCalls().isEmpty());
    }

    @Test
    void maps_a_tool_call_without_answer_text() {
        ModelTurn turn = mapper.toModelTurn(message(null, List.of(
                payload("call-1", "get_product_detail", "{\"productId\":42}"))));

        assertNull(turn.text());
        assertTrue(turn.hasToolCalls());
        assertEquals(1, turn.toolCalls().size());
        ToolCall call = turn.toolCalls().get(0);
        assertEquals("call-1", call.id());
        assertEquals("get_product_detail", call.name());
        assertEquals(42L, ((Number) call.arguments().get("productId")).longValue());
    }

    @Test
    void preserves_multiple_call_order_text_and_empty_arguments() {
        ModelTurn turn = mapper.toModelTurn(message("先查询两个工具", List.of(
                payload("first", "get_product_detail", "{\"productId\":42}"),
                payload("second", "list_my_orders", "{}"))));

        assertEquals("先查询两个工具", turn.text());
        assertEquals(List.of("first", "second"),
                turn.toolCalls().stream().map(ToolCall::id).toList());
        assertEquals("list_my_orders", turn.toolCalls().get(1).name());
        assertTrue(turn.toolCalls().get(1).arguments().isEmpty());
    }

    @Test
    void preserves_integer_range_and_decimal_precision() {
        ModelTurn turn = mapper.toModelTurn(message(null, List.of(payload(
                "precise", "sample", """
                {"integer":9223372036854775808,"decimal":1234567890.123456789}
                """))));

        Map<String, Object> arguments = turn.toolCalls().get(0).arguments();
        assertEquals(new BigInteger("9223372036854775808"), arguments.get("integer"));
        assertEquals(new BigDecimal("1234567890.123456789"), arguments.get("decimal"));
    }

    @Test
    void preserves_escaped_text_and_nested_argument_values() throws Exception {
        String original = "Java \"入门\"\n下一行";
        Map<String, Object> values = Map.of(
                "keyword", original,
                "flags", List.of(true, false),
                "filter", Map.of("enabled", true));
        String argumentsJson = new ObjectMapper().writeValueAsString(values);

        ModelTurn turn = mapper.toModelTurn(message(null, List.of(
                payload("nested", "sample", argumentsJson))));

        Map<String, Object> arguments = turn.toolCalls().get(0).arguments();
        assertEquals(original, arguments.get("keyword"));
        assertEquals(List.of(true, false), arguments.get("flags"));
        assertEquals(Map.of("enabled", true), arguments.get("filter"));
    }

    @Test
    void rejects_null_response_with_safe_error() {
        ModelCallException error =
                assertThrows(ModelCallException.class, () -> mapper.toModelTurn(null));
        assertEquals("模型返回了空响应", error.getMessage());
    }

    @Test
    void rejects_missing_or_blank_final_answers() {
        for (String text : Arrays.asList(null, "", " \n\t")) {
            assertThrows(ModelCallException.class,
                    () -> mapper.toModelTurn(new ModelMessage("assistant", text)));
        }
    }

    @Test
    void rejects_incomplete_or_non_function_tool_calls() {
        List<ModelMessage.ToolCallPayload> invalid = Arrays.asList(
                null,
                payload(null, "get_product_detail", "{}"),
                payload(" ", "get_product_detail", "{}"),
                new ModelMessage.ToolCallPayload(
                        "bad-type", "other",
                        new ModelMessage.FunctionPayload("get_product_detail", "{}")),
                new ModelMessage.ToolCallPayload("no-function", "function", null),
                payload("no-name", null, "{}"),
                payload("blank-name", " ", "{}"));

        for (ModelMessage.ToolCallPayload candidate : invalid) {
            ModelCallException error = assertThrows(ModelCallException.class,
                    () -> mapper.toModelTurn(message(
                            "不能用文本掩盖非法工具调用", Collections.singletonList(candidate))));
            assertEquals("模型工具调用格式错误", error.getMessage());
        }
    }

    @Test
    void rejects_non_object_malformed_and_trailing_argument_json() {
        List<String> invalid = Arrays.asList(
                null, "", " ", "not-json", "null", "[]", "42", "true",
                "{} {}", "{} trailing");
        for (String arguments : invalid) {
            ModelCallException error = assertThrows(ModelCallException.class,
                    () -> mapper.toModelTurn(message(
                            null, List.of(payload("bad-json", "sample", arguments)))));
            assertEquals("模型工具参数格式错误", error.getMessage());
        }
    }

    @Test
    void does_not_return_partial_calls_when_a_later_call_is_invalid() {
        ModelCallException error = assertThrows(ModelCallException.class,
                () -> mapper.toModelTurn(message("不能降级成普通回答", List.of(
                        payload("valid", "list_my_orders", "{}"),
                        payload("invalid", "get_product_detail", "[]")))));
        assertEquals("模型工具参数格式错误", error.getMessage());
        assertNotNull(error.getCause());
    }

    @Test
    void consumes_the_actual_wire_tool_calls_property() throws Exception {
        ObjectMapper json = new ObjectMapper();
        String body = json.writeValueAsString(Map.of(
                "role", "assistant",
                "tool_calls", List.of(Map.of(
                        "id", "wire-1",
                        "type", "function",
                        "function", Map.of(
                                "name", "get_product_detail",
                                "arguments", "{\"productId\":42}")))));

        ModelMessage response = json.readValue(body, ModelMessage.class);
        ModelTurn turn = mapper.toModelTurn(response);

        assertEquals("wire-1", turn.toolCalls().get(0).id());
        assertEquals("get_product_detail", turn.toolCalls().get(0).name());
        assertEquals(42L, ((Number) turn.toolCalls().get(0)
                .arguments().get("productId")).longValue());
    }

    private static ModelMessage message(
            String text, List<ModelMessage.ToolCallPayload> calls) {
        return new ModelMessage("assistant", text, calls, null);
    }

    private static ModelMessage.ToolCallPayload payload(
            String id, String name, String arguments) {
        return new ModelMessage.ToolCallPayload(id, "function",
                new ModelMessage.FunctionPayload(name, arguments));
    }
}

