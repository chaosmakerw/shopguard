package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.common.BizException;
import com.codesprint.lesson0831.security.CurrentUserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assistant/sessions")
public class AssistantHistoryController {

    private final ConversationMemory conversationMemory;
    private final CurrentUserContext currentUserContext;

    public AssistantHistoryController(
            ConversationMemory conversationMemory,
            CurrentUserContext currentUserContext
    ) {
        this.conversationMemory = conversationMemory;
        this.currentUserContext = currentUserContext;
    }

    @GetMapping("/{sessionId}/messages")
    public List<ChatMessage> messages(@PathVariable String sessionId) {
        Long userId = currentUserContext.getUserId();
        if (userId == null) {
            throw new BizException("UNAUTHORIZED", "请先登录");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("会话标识不能为空");
        }
        if (sessionId.length() > 100) {
            throw new IllegalArgumentException("会话标识不能超过100个字符");
        }

        // sessionId 只在当前认证用户内定位，客户端不能提供或覆盖 userId。
        return List.copyOf(conversationMemory.load(
                new ConversationKey(String.valueOf(userId), sessionId)));
    }
}
