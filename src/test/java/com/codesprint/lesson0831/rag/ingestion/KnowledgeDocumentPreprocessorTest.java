package com.codesprint.lesson0831.rag.ingestion;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeDocumentPreprocessorTest {

    @Test
    void shouldCleanAndNormalizeMarkdownWhilePreservingMeaningAndMetadata() {
        KnowledgeDocumentMetadata metadata =
                new KnowledgeDocumentMetadata(
                        "knowledge/example.md",
                        "order-lifecycle",
                        "2026-09-14",
                        DocumentVisibility.AUTHENTICATED
                );

        KnowledgeDocument source =
                new KnowledgeDocument(
                        metadata,
                        "# 交易规则  \r\n"
                                + "\r\n"
                                + "\r\n"
                                + "## 订单支付\t\r\n"
                                + "\r\n"
                                + "订单创建后30分钟内未支付。  \r\n"
                );

        KnowledgeDocumentPreprocessor preprocessor =
                new KnowledgeDocumentPreprocessor();

        KnowledgeDocument result =
                preprocessor.preprocess(source);

        assertSame(metadata, result.metadata());
        assertEquals(
                "交易规则\n\n"
                        + "订单支付\n\n"
                        + "订单创建后30分钟内未支付。",
                result.content()
        );
    }

    @Test
    void shouldPreprocessAllDocumentsAsImmutableList() {
        ClasspathKnowledgeDocumentLoader loader =
                new ClasspathKnowledgeDocumentLoader();
        List<KnowledgeDocument> sourceDocuments =
                loader.loadAll(
                        KnowledgeDocumentCatalog.standardMetadata()
                );

        KnowledgeDocumentPreprocessor preprocessor =
                new KnowledgeDocumentPreprocessor();

        List<KnowledgeDocument> results =
                preprocessor.preprocessAll(sourceDocuments);

        assertEquals(4, results.size());

        for (int index = 0;
             index < results.size();
             index++) {
            KnowledgeDocument result = results.get(index);

            assertEquals(
                    sourceDocuments.get(index).metadata(),
                    result.metadata()
            );
            assertFalse(result.content().contains("\r"));
            assertFalse(
                    Pattern.compile("(?m)^#{1,6}\\s+")
                            .matcher(result.content())
                            .find()
            );
        }

        assertThrows(
                UnsupportedOperationException.class,
                () -> results.clear()
        );
    }

    @Test
    void shouldRejectMissingOrEmptyDocumentListBeforeProcessing() {
        KnowledgeDocumentPreprocessor preprocessor =
                new KnowledgeDocumentPreprocessor();

        assertThrows(
                NullPointerException.class,
                () -> preprocessor.preprocess(null)
        );
        assertThrows(
                NullPointerException.class,
                () -> preprocessor.preprocessAll(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> preprocessor.preprocessAll(List.of())
        );
        assertThrows(
                NullPointerException.class,
                () -> preprocessor.preprocessAll(
                        Arrays.asList(
                                exampleDocument(),
                                null
                        )
                )
        );
    }

    @Test
    void shouldBeIdempotent() {
        KnowledgeDocumentPreprocessor preprocessor =
                new KnowledgeDocumentPreprocessor();

        KnowledgeDocument firstResult =
                preprocessor.preprocess(exampleDocument());
        KnowledgeDocument secondResult =
                preprocessor.preprocess(firstResult);

        assertEquals(firstResult, secondResult);
    }

    private KnowledgeDocument exampleDocument() {
        return new KnowledgeDocument(
                new KnowledgeDocumentMetadata(
                        "knowledge/example.md",
                        "example",
                        "2026-09-14",
                        DocumentVisibility.PUBLIC
                ),
                "# 示例标题  \r\n\r\n示例正文。  \r\n"
        );
    }
}
