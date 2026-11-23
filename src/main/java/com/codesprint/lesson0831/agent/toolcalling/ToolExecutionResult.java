package com.codesprint.lesson0831.agent.toolcalling;
import com.codesprint.lesson0831.product.ToolResult;

public record ToolExecutionResult(
        String toolCallId,
        ToolResult<?> result
) {
}
