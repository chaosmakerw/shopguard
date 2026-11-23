package com.codesprint.lesson0831.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GatewayResponseMapper {

    private static final ObjectReader ARGUMENT_READER =
            new ObjectMapper()
                    .readerFor(new TypeReference<Map<String, Object>>() {})
                    .with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            throw new ModelCallException("模型工具参数格式错误");
        }

        try {
            Map<String, Object> arguments = ARGUMENT_READER.readValue(json);

            if (arguments == null) {
                throw new ModelCallException("模型工具参数格式错误");
            }

            return arguments;
        } catch (JsonProcessingException exception) {
            throw new ModelCallException(
                    "模型工具参数格式错误",
                    exception
            );
        }
    }

    public ModelTurn toModelTurn(ModelMessage message) {
        if (message == null) {
            throw new ModelCallException("模型返回了空响应");
        }

        List<ModelMessage.ToolCallPayload> payloads =
                message.toolCalls();

        if (payloads == null || payloads.isEmpty()) {
            if (message.content() == null
                    || message.content().isBlank()) {
                throw new ModelCallException("模型返回了空响应");
            }

            return new ModelTurn(message.content(), List.of());
        }

        List<ToolCall> calls = new ArrayList<>();

        for (ModelMessage.ToolCallPayload payload : payloads) {
            if (payload == null
                    || payload.id() == null
                    || payload.id().isBlank()
                    || !"function".equals(payload.type())
                    || payload.function() == null
                    || payload.function().name() == null
                    || payload.function().name().isBlank()) {
                throw new ModelCallException("模型工具调用格式错误");
            }

            Map<String, Object> arguments =
                    parseArguments(payload.function().arguments());

            calls.add(new ToolCall(
                    payload.id(),
                    payload.function().name(),
                    arguments
            ));
        }

        return new ModelTurn(message.content(), calls);
    }
}
