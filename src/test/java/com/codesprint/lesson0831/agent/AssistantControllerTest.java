package com.codesprint.lesson0831.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import com.codesprint.lesson0831.security.NonProductionSecurityConfiguration;
import com.codesprint.lesson0831.security.CurrentUserContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(AssistantController.class)
@ActiveProfiles("test")
@Import(NonProductionSecurityConfiguration.class)
class AssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssistantService assistantService;

    @MockBean
    private CurrentUserContext currentUserContext;

    @Test
    void chat_should_bind_json_request_and_return_response()
            throws Exception {
        when(currentUserContext.getUserId()).thenReturn(1001L);
        when(assistantService.ask(any(ChatRequest.class)))
                .thenReturn(new ChatResponse("测试回答"));

        mockMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).header("X-Request-Id", "req-001")
                        .content("""
                                {
                                  "sessionId": "session-1",
                                  "question": "你好"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "answer": "测试回答"
                        }
                        """));
    }
    @Test
    void chat_should_ignore_client_user_id() throws Exception {
        when(currentUserContext.getUserId()).thenReturn(1001L);
        when(assistantService.ask(any(ChatRequest.class)))
                .thenReturn(new ChatResponse("测试回答"));
        mockMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).header("X-Request-Id", "req-001")
                        .content("""
                            {
                              "userId": "attacker",
                              "sessionId": "session-1",
                              "question": "你好"
                            }
                            """))
                .andExpect(status().isOk());

        ArgumentCaptor<ChatRequest> captor =
                ArgumentCaptor.forClass(ChatRequest.class);
        verify(assistantService).ask(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                "1001",
                captor.getValue().userId()
        );
    }
    @Test
    void chat_should_reject_blank_question() throws Exception {
        mockMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).header("X-Request-Id", "req-001")
                        .content("""
                            {
                              "sessionId": "session-1",
                              "question": ""
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("问题不能为空"));

        verifyNoInteractions(assistantService);
    }
    @Test
    void chat_should_log_trace_fields_without_sensitive_content(
            CapturedOutput output
    ) throws Exception {
        when(currentUserContext.getUserId())
                .thenReturn(1001L);

        when(assistantService.ask(
                any(ChatRequest.class)
        )).thenReturn(
                new ChatResponse("测试回答")
        );

        mockMvc.perform(
                        post("/api/assistant/chat")
                                .header(
                                        "X-Request-Id",
                                        "req-log-001"
                                )
                                .header(
                                        "Authorization",
                                        "Bearer JWT-SECRET-MARKER"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "sessionId": "session-1",
                                      "question": "PASSWORD-MARKER MODEL-API-KEY-MARKER"
                                    }
                                    """)
                )
                .andExpect(status().isOk());

        String logs = output.getAll();

        assertTrue(
                logs.contains(
                        "requestId=req-log-001"
                )
        );
        assertTrue(
                logs.contains(
                        "userId=1001"
                )
        );
        assertTrue(
                logs.contains(
                        "sessionId=session-1"
                )
        );
        assertTrue(
                logs.contains(
                        "resultCode=START"
                )
        );
        assertTrue(
                logs.contains(
                        "resultCode=SUCCESS"
                )
        );
        assertTrue(
                logs.matches(
                        "(?s).*latencyMs=\\d+.*"
                )
        );

        assertFalse(
                logs.contains(
                        "JWT-SECRET-MARKER"
                )
        );
        assertFalse(
                logs.contains(
                        "PASSWORD-MARKER"
                )
        );
        assertFalse(
                logs.contains(
                        "MODEL-API-KEY-MARKER"
                )
        );
        assertFalse(
                logs.contains(
                        "Authorization"
                )
        );
        assertFalse(
                logs.contains(
                        "Bearer"
                )
        );
        assertNull(MDC.get("requestId"));
        assertNull(MDC.get("userId"));
        assertNull(MDC.get("sessionId"));
    }
    @Test
    void chat_should_log_model_failure_and_return_502(
            CapturedOutput output
    ) throws Exception {
        when(currentUserContext.getUserId())
                .thenReturn(1001L);

        when(assistantService.ask(
                any(ChatRequest.class)
        )).thenThrow(
                new ModelCallException(
                        "MODEL-API-KEY-MARKER"
                )
        );

        mockMvc.perform(
                        post("/api/assistant/chat")
                                .header(
                                        "X-Request-Id",
                                        "req-failure-001"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                {
                                  "sessionId": "session-failure",
                                  "question": "PASSWORD-MARKER"
                                }
                                """)
                )
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message")
                        .value("模型服务暂时不可用"));

        String logs = output.getAll();

        assertTrue(
                logs.contains(
                        "requestId=req-failure-001"
                )
        );
        assertTrue(
                logs.contains(
                        "resultCode=MODEL_UNAVAILABLE"
                )
        );
        assertTrue(
                logs.contains(
                        "exceptionType=ModelCallException"
                )
        );
        assertTrue(
                logs.matches(
                        "(?s).*latencyMs=\\d+.*"
                )
        );

        assertFalse(
                logs.contains(
                        "MODEL-API-KEY-MARKER"
                )
        );
        assertFalse(
                logs.contains(
                        "PASSWORD-MARKER"
                )
        );
        assertNull(MDC.get("requestId"));
        assertNull(MDC.get("userId"));
        assertNull(MDC.get("sessionId"));
    }

    @Test
    void unsafeTraceValuesAreSanitizedWithoutChangingBusinessSession(
            CapturedOutput output
    ) throws Exception {
        when(currentUserContext.getUserId()).thenReturn(1001L);
        when(assistantService.ask(any(ChatRequest.class)))
                .thenReturn(new ChatResponse("测试回答"));

        mockMvc.perform(
                        post("/api/assistant/chat")
                                .header(
                                        "X-Request-Id",
                                        "PRIVATE=REQUEST-MARKER"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "sessionId": "session\\nPRIVATE-SESSION-MARKER",
                                      "question": "正常问题"
                                    }
                                    """)
                )
                .andExpect(status().isOk());

        ArgumentCaptor<ChatRequest> captor =
                ArgumentCaptor.forClass(ChatRequest.class);
        verify(assistantService).ask(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                "session\nPRIVATE-SESSION-MARKER",
                captor.getValue().sessionId()
        );

        String logs = output.getAll();
        assertTrue(logs.contains("sessionId=INVALID"));
        assertTrue(logs.matches(
                "(?s).*requestId=[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*"
        ));
        assertFalse(logs.contains("PRIVATE=REQUEST-MARKER"));
        assertFalse(logs.contains("PRIVATE-SESSION-MARKER"));
        assertNull(MDC.get("requestId"));
        assertNull(MDC.get("userId"));
        assertNull(MDC.get("sessionId"));
    }
}
