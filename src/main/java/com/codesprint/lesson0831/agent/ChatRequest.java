package com.codesprint.lesson0831.agent;
import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank(message = "用户身份不能为空")
        String userId,

        @NotBlank(message = "会话标识不能为空")
        String sessionId,

        @NotBlank(message = "问题不能为空")
        String question
) {
}