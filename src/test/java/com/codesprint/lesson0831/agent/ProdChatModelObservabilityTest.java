package com.codesprint.lesson0831.agent;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.config.ModelProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProdChatModelObservabilityTest {

    @Test
    void successfulTurnLogsTraceFieldsWithoutModelContent() {
        Logger logger = (Logger) LoggerFactory.getLogger(ProdChatModel.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.put("requestId", "req-model-success");

        try {
            ModelProperties properties = properties();
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(properties.getBaseUrl());
            MockRestServiceServer server =
                    MockRestServiceServer.bindTo(builder).build();
            ProdChatModel model = new ProdChatModel(properties, builder.build());

            server.expect(requestTo("http://model.test/v1/chat/completions"))
                    .andRespond(withSuccess("""
                            {
                              "choices": [{
                                "message": {
                                  "role": "assistant",
                                  "content": "PRIVATE-ANSWER-MARKER"
                                }
                              }]
                            }
                            """, MediaType.APPLICATION_JSON));

            ModelTurn turn = model.nextTurn(input("PRIVATE-QUESTION-MARKER"));

            assertEquals("PRIVATE-ANSWER-MARKER", turn.text());
            server.verify();

            String logs = captured(appender);
            assertTrue(logs.contains("requestId=req-model-success"));
            assertTrue(logs.contains("resultCode=SUCCESS"));
            assertTrue(logs.matches("(?s).*latencyMs=\\d+.*"));
            assertFalse(logs.contains("test-api-key"));
            assertFalse(logs.contains("PRIVATE-QUESTION-MARKER"));
            assertFalse(logs.contains("PRIVATE-ANSWER-MARKER"));
        } finally {
            MDC.remove("requestId");
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void invalidModelContentLogsStableFailureWithoutContent() {
        Logger logger = (Logger) LoggerFactory.getLogger(ProdChatModel.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.put("requestId", "req-model-invalid");

        try {
            ModelProperties properties = properties();
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(properties.getBaseUrl());
            MockRestServiceServer server =
                    MockRestServiceServer.bindTo(builder).build();
            ProdChatModel model = new ProdChatModel(properties, builder.build());

            server.expect(requestTo("http://model.test/v1/chat/completions"))
                    .andRespond(withSuccess("""
                            {
                              "choices": [{
                                "message": {
                                  "role": "assistant",
                                  "content": ""
                                }
                              }]
                            }
                            """, MediaType.APPLICATION_JSON));

            assertThrows(
                    ModelCallException.class,
                    () -> model.nextTurn(input("PRIVATE-INVALID-QUESTION"))
            );
            server.verify();

            String logs = captured(appender);
            assertTrue(logs.contains("requestId=req-model-invalid"));
            assertTrue(logs.contains("resultCode=MODEL_UNAVAILABLE"));
            assertTrue(logs.contains("exceptionType=com.codesprint.lesson0831.agent.ModelCallException"));
            assertTrue(logs.matches("(?s).*latencyMs=\\d+.*"));
            assertFalse(logs.contains("PRIVATE-INVALID-QUESTION"));
        } finally {
            MDC.remove("requestId");
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private ModelProperties properties() {
        ModelProperties properties = new ModelProperties();
        properties.setBaseUrl("http://model.test");
        properties.setApiKey("test-api-key");
        properties.setName("demo-model");
        return properties;
    }

    private ModelInput input(String question) {
        return new ModelInput(
                new ChatRequest("user-a", "session-1", question),
                List.of(),
                List.of()
        );
    }

    private String captured(ListAppender<ILoggingEvent> appender) {
        StringBuilder captured = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            captured.append(event.getFormattedMessage()).append('\n');
        }
        return captured.toString();
    }
}
