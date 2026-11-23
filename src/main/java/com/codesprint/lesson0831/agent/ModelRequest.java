package com.codesprint.lesson0831.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelRequest(
        String model,
        List<ModelMessage> messages,
        List<Map<String, Object>> tools
) {
    public ModelRequest(String model, List<ModelMessage> messages) {
        this(model, messages, null);
    }
}