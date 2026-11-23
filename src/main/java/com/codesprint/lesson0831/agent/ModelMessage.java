package com.codesprint.lesson0831.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelMessage(
        String role,
        String content,
        @JsonProperty("tool_calls")
        List<ToolCallPayload> toolCalls,
        @JsonProperty("tool_call_id")
        String toolCallId
) {
    public ModelMessage(String role, String content) {
        this(role, content, null, null);
    }

    public record ToolCallPayload(
            String id,
            String type,
            FunctionPayload function
    ) {}

    public record FunctionPayload(
            String name,
            String arguments
    ) {}
}