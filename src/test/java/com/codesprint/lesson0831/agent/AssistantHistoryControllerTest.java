package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.security.CurrentUserContext;
import com.codesprint.lesson0831.security.NonProductionSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC 参数和身份隔离测试；不访问网络、数据库或模型。 */
@WebMvcTest(AssistantHistoryController.class)
@ActiveProfiles("test")
@Import(NonProductionSecurityConfiguration.class)
class AssistantHistoryControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ConversationMemory memory;
    @MockBean CurrentUserContext currentUserContext;

    @Test
    void returnsOnlyCurrentUsersMessagesInStoredOrder() throws Exception {
        when(currentUserContext.getUserId()).thenReturn(1001L);
        when(memory.load(new ConversationKey("1001", "shared-session"))).thenReturn(List.of(
                new ChatMessage("user", "我的订单", 1),
                new ChatMessage("assistant", "你有一笔订单", 2)));

        mvc.perform(get("/api/assistant/sessions/shared-session/messages")
                        .queryParam("userId", "1002"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {"role":"user","content":"我的订单","sequence":1},
                          {"role":"assistant","content":"你有一笔订单","sequence":2}
                        ]
                        """, true));

        verify(memory).load(new ConversationKey("1001", "shared-session"));
        verifyNoMoreInteractions(memory);
    }

    @Test
    void anotherUserCannotReadMessagesUsingTheSameSessionId() throws Exception {
        when(currentUserContext.getUserId()).thenReturn(1002L);
        when(memory.load(new ConversationKey("1002", "shared-session"))).thenReturn(List.of());

        mvc.perform(get("/api/assistant/sessions/shared-session/messages")
                        .queryParam("userId", "1001"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));

        verify(memory).load(new ConversationKey("1002", "shared-session"));
        verifyNoMoreInteractions(memory);
    }

    @Test
    void rejectsMissingAuthenticatedIdentityBeforeReadingMemory() throws Exception {
        when(currentUserContext.getUserId()).thenReturn(null);

        mvc.perform(get("/api/assistant/sessions/session-1/messages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("请先登录"));

        verifyNoInteractions(memory);
    }

    @Test
    void rejectsBlankSessionIdBeforeReadingMemory() throws Exception {
        when(currentUserContext.getUserId()).thenReturn(1001L);

        mvc.perform(get("/api/assistant/sessions/{sessionId}/messages", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("会话标识不能为空"));

        verifyNoInteractions(memory);
    }

    @Test
    void rejectsSessionIdLongerThanStorageLimitBeforeReadingMemory() throws Exception {
        when(currentUserContext.getUserId()).thenReturn(1001L);

        mvc.perform(get("/api/assistant/sessions/{sessionId}/messages", "s".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("会话标识不能超过100个字符"));

        verifyNoInteractions(memory);
    }

    @Test
    void acceptsSessionIdAtStorageLimitAndReturnsEmptyHistory() throws Exception {
        when(currentUserContext.getUserId()).thenReturn(1001L);
        when(memory.load(new ConversationKey("1001", "s".repeat(100)))).thenReturn(List.of());

        mvc.perform(get("/api/assistant/sessions/{sessionId}/messages", "s".repeat(100)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));
    }
}
