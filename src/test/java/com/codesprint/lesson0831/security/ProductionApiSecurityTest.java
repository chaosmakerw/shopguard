package com.codesprint.lesson0831.security;

import com.codesprint.lesson0831.agent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 Spring Security Bearer 过滤链 + 替身 JWT 解码器；不访问外部 JWK、数据库或模型。 */
@WebMvcTest(controllers = {AssistantController.class, AssistantHistoryController.class},
        properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://local-test-issuer")
@ActiveProfiles("prod")
@Import({SecurityConfiguration.class, SpringSecurityPrincipalProvider.class})
class ProductionApiSecurityTest {

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean ConversationMemory memory;
    @MockBean AssistantService assistant;

    @BeforeEach
    void configureToken() {
        when(jwtDecoder.decode("valid-token")).thenReturn(token("valid-token", "1001"));
    }

    @Test
    void historyRequiresBearerAuthentication() throws Exception {
        mvc.perform(get("/api/assistant/sessions/private-session/messages"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(memory);
    }

    @Test
    void decoderRejectsInvalidTokenBeforeControllerRuns() throws Exception {
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid token"));

        mvc.perform(get("/api/assistant/sessions/private-session/messages")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(memory);
    }

    @Test
    void verifiedJwtSubjectSelectsHistoryAndDoesNotCreateSession() throws Exception {
        when(memory.load(new ConversationKey("1001", "private-session")))
                .thenReturn(List.of(new ChatMessage("assistant", "stored answer", 2)));

        var response = mvc.perform(get("/api/assistant/sessions/private-session/messages")
                        .header("Authorization", "Bearer valid-token")
                        .queryParam("userId", "1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("stored answer"))
                .andReturn();

        assertNull(response.getRequest().getSession(false));
        verify(memory).load(new ConversationKey("1001", "private-session"));
    }

    @Test
    void sessionCookieCannotReplaceBearerAuthentication() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated("1001", "unused", List.of())));

        mvc.perform(get("/api/assistant/sessions/private-session/messages").session(session))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(memory);
    }

    @Test
    void validBearerCanPostWithoutCsrfToken() throws Exception {
        when(assistant.ask(any(ChatRequest.class))).thenReturn(new ChatResponse("ok"));

        mvc.perform(post("/api/assistant/chat")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"private-session","question":"你好"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("ok"));

        verify(assistant).ask(new ChatRequest("1001", "private-session", "你好"));
    }

    @Test
    void unauthenticatedPostIsRejectedBeforeAssistantRuns() throws Exception {
        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"private-session","question":"你好"}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(assistant);
    }

    @Test
    void nonNumericJwtSubjectCannotReadAnyConversation() throws Exception {
        when(jwtDecoder.decode("invalid-subject")).thenReturn(token("invalid-subject", "attacker"));

        mvc.perform(get("/api/assistant/sessions/private-session/messages")
                        .header("Authorization", "Bearer invalid-subject"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(memory);
    }

    @Test
    void productionDeniesDemoRoutesEvenForAuthenticatedUsers() throws Exception {
        mvc.perform(get("/api/demo/info").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/demo/index.html").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void productionDoesNotExposeActuatorAnonymously() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyGetRequestsToMinimalHealthProbesBypassAuthentication() throws Exception {
        // MVC 切片没有装配 Actuator；404 表示通过过滤链后才由路由判定不存在。
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/actuator/health/liveness"))
                .andExpect(status().isUnauthorized());
    }

    private Jwt token(String tokenValue, String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject(subject)
                .claim("roles", List.of("USER"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
