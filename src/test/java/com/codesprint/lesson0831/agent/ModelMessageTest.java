package com.codesprint.lesson0831.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelMessageTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReadToolCallFromJson() throws Exception {
        String json = """
                {
                  "role": "assistant",
                  "content": null,
                  "tool_calls": [
                    {
                      "id": "call-001",
                      "type": "function",
                      "function": {
                        "name": "list_my_orders",
                        "arguments": "{}"
                      }
                    }
                  ]
                }
                """;

        ModelMessage message =
                objectMapper.readValue(json, ModelMessage.class);

        assertEquals("assistant", message.role());
        assertNull(message.content());
        assertEquals(1, message.toolCalls().size());

        ModelMessage.ToolCallPayload call =
                message.toolCalls().get(0);

        assertEquals("call-001", call.id());
        assertEquals("function", call.type());
        assertEquals("list_my_orders", call.function().name());
        assertEquals("{}", call.function().arguments());
    }
    @Test
    void shouldKeepPlainTextMessageShape() throws Exception {
        ModelMessage message = new ModelMessage("user", "你好");

        String actual = objectMapper.writeValueAsString(message);

        assertEquals(
                objectMapper.readTree("""
                    {"role":"user","content":"你好"}
                    """),
                objectMapper.readTree(actual)
        );
    }
}