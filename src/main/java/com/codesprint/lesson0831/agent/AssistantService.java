package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.agent.toolcalling.ToolCallingRunner;
import com.codesprint.lesson0831.common.BizException;
import com.codesprint.lesson0831.security.CurrentUserContext;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AssistantService {

    private static final int MAX_HISTORY_MESSAGES = 10;
    // 固定数量避免每个新会话创建永久锁；只保证同一 JVM 的问答顺序。
    private static final ReentrantLock[] CONVERSATION_LOCKS = createConversationLocks();

    private final ContextWindow contextWindow =
            new ContextWindow();

    private final ConversationMemory conversationMemory;
    private final ToolCallingRunner toolCallingRunner;
    private final CurrentUserContext currentUserContext;

    @Autowired
    public AssistantService(
            ToolCallingRunner toolCallingRunner,
            ConversationMemory conversationMemory,
            CurrentUserContext currentUserContext
    ) {
        this.toolCallingRunner = toolCallingRunner;
        this.conversationMemory = conversationMemory;
        this.currentUserContext = currentUserContext;
    }

    public AssistantService(
            ToolCallingRunner toolCallingRunner,
            ConversationMemory conversationMemory
    ) {
        this(toolCallingRunner, conversationMemory, null);
    }

    public ChatResponse ask(ChatRequest request) {
        return ask(request, null);
    }

    /** Keep server-provided context out of the user's message and conversation title. */
    public ChatResponse ask(ChatRequest request, String context) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "请求不能为空"
            );
        }

        String userKey;
        if (currentUserContext == null) {
            if (request.userId() == null || request.userId().isBlank()) {
                throw new IllegalArgumentException("用户身份不能为空");
            }
            userKey = request.userId();
        } else {
            Long userId = currentUserContext.getUserId();
            if (userId == null) {
                throw new BizException("UNAUTHORIZED", "请先登录");
            }
            userKey = String.valueOf(userId);
        }

        if (userKey.length() > 100) {
            throw new IllegalArgumentException("用户标识不能超过100个字符");
        }

        if (request.sessionId() == null
                || request.sessionId().isBlank()) {
            throw new IllegalArgumentException(
                    "会话标识不能为空"
            );
        }

        if (request.sessionId().length() > 100) {
            throw new IllegalArgumentException("会话标识不能超过100个字符");
        }

        if (request.question() == null
                || request.question().isBlank()) {
            throw new IllegalArgumentException(
                    "问题不能为空"
            );
        }

        ConversationKey key = new ConversationKey(
                userKey,
                request.sessionId()
        );

        ReentrantLock conversationLock = CONVERSATION_LOCKS[
                Math.floorMod(key.hashCode(), CONVERSATION_LOCKS.length)];
        conversationLock.lock();
        try {
            List<ChatMessage> history = List.copyOf(
                    contextWindow.select(conversationMemory.load(key), MAX_HISTORY_MESSAGES));
            ChatRequest modelRequest = context == null || context.isBlank() ? request
                    : new ChatRequest(request.userId(), request.sessionId(), request.question() + "\n" + context);
            String answer = toolCallingRunner.run(modelRequest, history);
            conversationMemory.appendTurn(key, request.question(), answer);
            return new ChatResponse(answer);
        } finally {
            conversationLock.unlock();
        }
    }

    private static ReentrantLock[] createConversationLocks() {
        ReentrantLock[] locks = new ReentrantLock[64];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new ReentrantLock();
        }
        return locks;
    }
}
