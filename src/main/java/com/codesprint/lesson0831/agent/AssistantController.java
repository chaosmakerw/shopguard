package com.codesprint.lesson0831.agent;

import jakarta.validation.Valid;
import com.codesprint.lesson0831.security.CurrentUserContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.regex.Pattern;
import java.util.UUID;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final CurrentUserContext currentUserContext;
    private static final Logger log =
            LoggerFactory.getLogger(AssistantController.class);
    private static final Pattern SAFE_TRACE_VALUE =
            Pattern.compile(
                    "[A-Za-z0-9._-]{1,64}"
            );
    public AssistantController(
            AssistantService assistantService,
            CurrentUserContext currentUserContext
    ) {
        this.assistantService = assistantService;
        this.currentUserContext = currentUserContext;
    }

    @PostMapping("/chat")
    public ChatResponse chat(
            @RequestHeader(value = "X-Request-Id", required = false)
            String requestId,
            @Valid @RequestBody AssistantHttpRequest request
    ) {
        String actualRequestId =
                safeTraceValue(
                        requestId,
                        UUID.randomUUID().toString()
                );
        long startNanos = System.nanoTime();
        Long currentUserId = currentUserContext.getUserId();
        if (currentUserId == null) {
            throw new com.codesprint.lesson0831.common.BizException(
                    "UNAUTHORIZED",
                    "请先登录"
            );
        }
        String userKey = String.valueOf(currentUserId);
        String logSessionId =
                safeTraceValue(
                        request.sessionId(),
                        "INVALID"
                );
        MDC.put("requestId", actualRequestId);
        MDC.put("userId", userKey);
        MDC.put("sessionId", logSessionId);
        log.info(
                "assistant request requestId={} userId={} sessionId={} resultCode=START",
                actualRequestId,
                userKey,
                logSessionId
        );
        try {
            ChatResponse response = assistantService.ask(
                    new ChatRequest(
                            userKey,
                            request.sessionId(),
                            request.question()
                    )
            );

            long latencyMs =
                    (System.nanoTime() - startNanos)
                            / 1_000_000;

            log.info(
                    "assistant request requestId={} userId={} sessionId={} latencyMs={} resultCode=SUCCESS",
                    actualRequestId,
                    userKey,
                    logSessionId,
                    latencyMs
            );

            return response;
        } catch (ModelCallException exception) {
            long latencyMs =
                    (System.nanoTime() - startNanos)
                            / 1_000_000;

            log.warn(
                    "assistant request requestId={} userId={} sessionId={} latencyMs={} resultCode=MODEL_UNAVAILABLE exceptionType={}",
                    actualRequestId,
                    userKey,
                    logSessionId,
                    latencyMs,
                    exception.getClass().getSimpleName()
            );

            throw exception;
        }
        finally {
            MDC.remove("requestId");
            MDC.remove("userId");
            MDC.remove("sessionId");
        }
    }
    private static String safeTraceValue(
            String value,
            String fallback
    ) {
        if (value == null
                || !SAFE_TRACE_VALUE
                .matcher(value)
                .matches()) {
            return fallback;
        }

        return value;
    }
}
