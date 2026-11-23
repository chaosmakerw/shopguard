package com.codesprint.lesson0831.agent.toolcalling;
import java.util.Map;
public record ToolCall(
        String id,
        String name,
        Map<String,Object> arguments
) {
}
