package com.codesprint.lesson0831.rag.ingestion;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasspathKnowledgeDocumentLoaderTest {

    @Test
    void shouldDescribeFourStandardKnowledgeSources() {
        List<KnowledgeDocumentMetadata> metadataList =
                KnowledgeDocumentCatalog.standardMetadata();

        assertEquals(4, metadataList.size());

        Set<String> sources = new HashSet<>();

        for (KnowledgeDocumentMetadata metadata
                : metadataList) {
            sources.add(metadata.source());
            assertEquals(
                    "2026-09-14",
                    metadata.version()
            );
        }

        assertEquals(
                Set.of(
                        "knowledge/trading-rules.md",
                        "knowledge/refund-policy.md",
                        "knowledge/security-guide.md",
                        "knowledge/faq.md"
                ),
                sources
        );

        assertEquals(
                DocumentVisibility.ADMIN_ONLY,
                findMetadata(
                        metadataList,
                        "knowledge/security-guide.md"
                ).visibility()
        );

        assertEquals(
                DocumentVisibility.PUBLIC,
                findMetadata(
                        metadataList,
                        "knowledge/faq.md"
                ).visibility()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> metadataList.clear()
        );
    }

    @Test
    void shouldLoadAllStandardDocumentsAsUtf8() {
        ClasspathKnowledgeDocumentLoader loader =
                new ClasspathKnowledgeDocumentLoader();

        List<KnowledgeDocument> documents =
                loader.loadAll(
                        KnowledgeDocumentCatalog.standardMetadata()
                );

        assertEquals(4, documents.size());

        assertTrue(containsText(
                documents,
                "订单创建后30分钟内未支付"
        ));
        assertTrue(containsText(
                documents,
                "已发货订单"
        ));
        assertTrue(containsText(
                documents,
                "身份必须来自服务器可信上下文"
        ));
        assertTrue(containsText(
                documents,
                "如何查看我的订单"
        ));

        assertThrows(
                UnsupportedOperationException.class,
                () -> documents.clear()
        );
    }

    @Test
    void shouldRejectMissingResource() {
        ClasspathKnowledgeDocumentLoader loader =
                new ClasspathKnowledgeDocumentLoader();

        KnowledgeDocumentMetadata metadata =
                new KnowledgeDocumentMetadata(
                        "knowledge/missing.md",
                        "missing",
                        "2026-09-14",
                        DocumentVisibility.PUBLIC
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> loader.load(metadata)
                );

        assertEquals(
                "知识文档不存在: knowledge/missing.md",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMissingOrEmptySourceListBeforeReading() {
        ClasspathKnowledgeDocumentLoader loader =
                new ClasspathKnowledgeDocumentLoader();

        assertThrows(
                NullPointerException.class,
                () -> loader.loadAll(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> loader.loadAll(List.of())
        );

        assertThrows(
                NullPointerException.class,
                () -> loader.loadAll(
                        Arrays.asList(
                                KnowledgeDocumentCatalog
                                        .standardMetadata()
                                        .get(0),
                                null
                        )
                )
        );
    }

    private KnowledgeDocumentMetadata findMetadata(
            List<KnowledgeDocumentMetadata> metadataList,
            String source
    ) {
        return metadataList.stream()
                .filter(metadata ->
                        metadata.source().equals(source))
                .findFirst()
                .orElseThrow();
    }

    private boolean containsText(
            List<KnowledgeDocument> documents,
            String expectedText
    ) {
        return documents.stream()
                .map(KnowledgeDocument::content)
                .anyMatch(content ->
                        content.contains(expectedText));
    }
}
