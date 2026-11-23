package com.codesprint.lesson0831.rag.retrieval;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.TextEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalLogPrivacyTest {

    @Test
    void vectorFaultLogMustNotExposeExceptionMessageOrStackTrace() {
        Logger logger = (Logger) LoggerFactory.getLogger(RetrievalService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.put("requestId", "req-retrieval-failure");

        try {
            TextEmbeddingModel embedding = text -> List.of(1.0, 0.0);
            VectorSearchPort search = (vector, version, visibility, topK) -> {
                throw new IllegalStateException("secret-key=demo-secret; host=internal-vector-address");
            };
            RetrievalService service = new RetrievalService(embedding, search);

            RetrievalResult result = service.retrieve(new RetrievalRequest(
                    "我的订单如何退货？", "v1", Set.of(DocumentVisibility.PUBLIC), 3, 0.7));

            assertEquals(RetrievalCode.VECTOR_STORE_UNAVAILABLE, result.code());
            StringBuilder captured = new StringBuilder();
            for (ILoggingEvent event : appender.list) {
                captured.append(event.getFormattedMessage()).append('\n');
                if (event.getThrowableProxy() != null) {
                    captured.append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
                }
            }
            String logs = captured.toString();
            assertTrue(logs.contains("requestId=req-retrieval-failure"));
            assertTrue(logs.contains("stage=VECTOR_SEARCH"));
            assertTrue(logs.contains("resultCode=VECTOR_STORE_UNAVAILABLE"));
            assertTrue(logs.contains("exceptionType=java.lang.IllegalStateException"));
            assertTrue(logs.matches("(?s).*latencyMs=\\d+.*"));
            assertFalse(logs.contains("demo-secret"));
            assertFalse(logs.contains("internal-vector-address"));
            assertFalse(logs.contains("我的订单如何退货"));
        } finally {
            MDC.remove("requestId");
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void noResultLogContainsTraceFieldsWithoutQueryText() {
        Logger logger = (Logger) LoggerFactory.getLogger(RetrievalService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.put("requestId", "req-retrieval-empty");

        try {
            TextEmbeddingModel embedding = text -> List.of(1.0, 0.0);
            VectorSearchPort search =
                    (vector, version, visibility, topK) -> List.of();
            RetrievalService service = new RetrievalService(embedding, search);

            RetrievalResult result = service.retrieve(new RetrievalRequest(
                    "PRIVATE-QUERY-MARKER", "v1",
                    Set.of(DocumentVisibility.PUBLIC), 3, 0.7));

            assertEquals(RetrievalCode.NO_RESULT, result.code());

            StringBuilder captured = new StringBuilder();
            for (ILoggingEvent event : appender.list) {
                captured.append(event.getFormattedMessage()).append('\n');
            }
            String logs = captured.toString();

            assertTrue(logs.contains("requestId=req-retrieval-empty"));
            assertTrue(logs.contains("resultCode=NO_RESULT"));
            assertTrue(logs.matches("(?s).*latencyMs=\\d+.*"));
            assertFalse(logs.contains("PRIVATE-QUERY-MARKER"));
        } finally {
            MDC.remove("requestId");
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
