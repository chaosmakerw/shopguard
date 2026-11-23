package com.codesprint.lesson0831.agent;

import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssistantHttpRequest(
        @NotBlank(message = "会话标识不能为空")
        String sessionId,

        @NotBlank(message = "问题不能为空")
        String question
) {
}
