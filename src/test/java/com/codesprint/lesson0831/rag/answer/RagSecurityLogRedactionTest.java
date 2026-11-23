package com.codesprint.lesson0831.rag.answer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.codesprint.lesson0831.rag.ingestion.TextEmbeddingModel;
import com.codesprint.lesson0831.rag.retrieval.RetrievalRequest;
import com.codesprint.lesson0831.rag.retrieval.RetrievalService;
import com.codesprint.lesson0831.rag.retrieval.RetrievedChunk;
import com.codesprint.lesson0831.rag.retrieval.VectorSearchPort;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RagSecurityLogRedactionTest {

    private static final String SECRET_QUESTION = "查询 TOKEN_QUERY_MARKER 的处理规则";
    private static final String SECRET_DOCUMENT =
            "DOCUMENT_PRIVATE_MARKER：内部处理规则，不应进入日志。";
    private static final String SECRET_EXCEPTION = "api-key=MODEL_KEY_MARKER";

    @Test
    void generationFailureKeepsQuestionDocumentAndExceptionMessageOutOfLogs() {
        Logger logger = logger(RagAnswerService.class);
        ListAppender<ILoggingEvent> appender = attach(logger);

        try {
            TextEmbeddingModel embedding = text -> List.of(1.0, 0.0);
            VectorSearchPort search = (vector, version, visibility, topK) ->
                    List.of(sensitiveChunk());
            RagAnswerService service = new RagAnswerService(
                    new RetrievalService(embedding, search),
                    new RagPromptBuilder(),
                    prompt -> {
                        throw new IllegalStateException(
                                SECRET_EXCEPTION + "; " + SECRET_DOCUMENT);
                    });

            RagAnswerResult result = service.answer(request());

            assertEquals(RagAnswerCode.GENERATION_UNAVAILABLE, result.code());
            assertFalse(result.success());
            assertTrue(captured(appender).contains("RAG generation failed"));
            assertNoSensitiveValues(captured(appender));
            assertFalse(result.answer().contains("MODEL_KEY_MARKER"));
            assertFalse(result.answer().contains("DOCUMENT_PRIVATE_MARKER"));
        } finally {
            detach(logger, appender);
        }
    }

    @Test
    void embeddingFailureKeepsQuestionAndExceptionMessageOutOfLogs() {
        Logger logger = logger(RagAnswerService.class);
        ListAppender<ILoggingEvent> appender = attach(logger);

        try {
            TextEmbeddingModel embedding = text -> {
                throw new IllegalStateException(SECRET_EXCEPTION + "; " + text);
            };
            VectorSearchPort search = mock(VectorSearchPort.class);
            RagAnswerGenerator generator = mock(RagAnswerGenerator.class);
            RagAnswerService service = new RagAnswerService(
                    new RetrievalService(embedding, search),
                    new RagPromptBuilder(), generator);

            RagAnswerResult result = service.answer(request());

            assertEquals(RagAnswerCode.RETRIEVAL_UNAVAILABLE, result.code());
            assertTrue(captured(appender).contains("RAG retrieval failed"));
            assertNoSensitiveValues(captured(appender));
            verifyNoInteractions(search, generator);
        } finally {
            detach(logger, appender);
        }
    }

    @Test
    void successfulFlowDoesNotLogSensitiveRetrievedDocument() {
        Logger answerLogger = logger(RagAnswerService.class);
        Logger retrievalLogger = logger(RetrievalService.class);
        ListAppender<ILoggingEvent> answerAppender = attach(answerLogger);
        ListAppender<ILoggingEvent> retrievalAppender = attach(retrievalLogger);

        try {
            TextEmbeddingModel embedding = text -> List.of(1.0, 0.0);
            VectorSearchPort search = (vector, version, visibility, topK) ->
                    List.of(sensitiveChunk());
            RagAnswerService service = new RagAnswerService(
                    new RetrievalService(embedding, search),
                    new RagPromptBuilder(),
                    prompt -> RagGenerationResult.answered(
                            "请联系人工客服。",
                            List.of("internal-example")
                    ));

            RagAnswerResult result = service.answer(request());

            assertEquals(RagAnswerCode.ANSWERED, result.code());
            assertTrue(answerAppender.list.isEmpty());
            String retrievalLogs = captured(retrievalAppender);
            assertTrue(retrievalLogs.contains("resultCode=SUCCESS"));
            assertTrue(retrievalLogs.matches(
                    "(?s).*latencyMs=\\d+.*"
            ));
            assertNoSensitiveValues(retrievalLogs);
        } finally {
            detach(answerLogger, answerAppender);
            detach(retrievalLogger, retrievalAppender);
        }
    }

    private RetrievedChunk sensitiveChunk() {
        return new RetrievedChunk(
                new KnowledgeChunk("internal-example", 0, SECRET_DOCUMENT,
                        new KnowledgeDocumentMetadata("knowledge/internal.md",
                                "rules", "v1", DocumentVisibility.AUTHENTICATED)),
                0.9);
    }

    private RetrievalRequest request() {
        return new RetrievalRequest(SECRET_QUESTION, "v1",
                Set.of(DocumentVisibility.AUTHENTICATED), 3, 0.7);
    }

    private Logger logger(Class<?> type) {
        return (Logger) LoggerFactory.getLogger(type);
    }

    private ListAppender<ILoggingEvent> attach(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detach(Logger logger, ListAppender<ILoggingEvent> appender) {
        logger.detachAppender(appender);
        appender.stop();
    }

    private String captured(ListAppender<ILoggingEvent> appender) {
        StringBuilder text = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            text.append(event.getFormattedMessage()).append('\n');
            if (event.getThrowableProxy() != null) {
                text.append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            }
        }
        return text.toString();
    }

    private void assertNoSensitiveValues(String output) {
        assertFalse(output.contains(SECRET_QUESTION));
        assertFalse(output.contains("TOKEN_QUERY_MARKER"));
        assertFalse(output.contains(SECRET_DOCUMENT));
        assertFalse(output.contains("DOCUMENT_PRIVATE_MARKER"));
        assertFalse(output.contains("MODEL_KEY_MARKER"));
    }
}
