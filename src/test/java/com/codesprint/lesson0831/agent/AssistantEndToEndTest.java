package com.codesprint.lesson0831.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AssistantEndToEndTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ConversationMemory conversationMemory;
    @Test
    void should_chat_through_real_spring_chain() throws Exception {
        mockMvc.perform(
                        post("/api/assistant/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                          {
                                          "sessionId": "session-1",
                                          "question": "你好"
                                        }""")
                ).andExpect(status().isOk())
                .andExpect(content().encoding("UTF-8"))
                .andExpect(header().string("Content-Type", "application/json;charset=UTF-8"))
                .andExpect(
                        jsonPath("$.answer")
                                .value("[fake-test] 这是测试模型的回答：你好")
                );
        mockMvc.perform(
                        post("/api/assistant/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                    {
                      "sessionId":"session-1",
                      "question":"你好，我是 B"
                    }
                """)
                )
                .andExpect(status().isOk());
        List<ChatMessage> messages =
                conversationMemory.load(
                        new ConversationKey(
                                "1001",
                                "session-1"
                        )
                );
        List<ChatMessage> userBMessages =
                conversationMemory.load(
                        new ConversationKey(
                                "1001",
                                "session-1"
                        )
                );
        assertEquals(4,messages.size());
        assertEquals("user", messages.get(0).role());
        assertEquals("assistant", messages.get(1).role());
        assertEquals("你好", messages.get(0).content());
        assertEquals(
                "[fake-test] 这是测试模型的回答：你好",
                messages.get(1).content()
        );
        assertEquals(4, userBMessages.size());
        assertEquals(
                "你好",
                userBMessages.get(0).content()
        );
        assertEquals("assistant", userBMessages.get(1).role());
        assertEquals(
                "[fake-test] 这是测试模型的回答：你好",
                userBMessages.get(1).content()
        );
        assertEquals("你好，我是 B", userBMessages.get(2).content());
        assertEquals(
                "[fake-test] 这是测试模型的回答：你好，我是 B",
                userBMessages.get(3).content()
        );
    }

    @Test
    void should_reject_blank_question_before_entering_business_flow() throws Exception {
        mockMvc.perform(
                        post("/api/assistant/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "invalid-user",
                                          "sessionId": "invalid-session",
                                          "question": "   "
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("问题不能为空"));
    }

}
