package com.codesprint.lesson0831.rag.ingestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeDocumentTest {

    @Test
    void shouldCreateDocumentWithRequiredMetadata() {
        KnowledgeDocumentMetadata metadata =
                new KnowledgeDocumentMetadata(
                        "knowledge/trading-rules.md",
                        "payment-timeout",
                        "2026-09-14",
                        DocumentVisibility.AUTHENTICATED
                );

        KnowledgeDocument document =
                new KnowledgeDocument(
                        metadata,
                        "订单创建后30分钟内未支付会自动关闭。"
                );

        assertSame(metadata, document.metadata());
        assertEquals(
                "knowledge/trading-rules.md",
                document.metadata().source()
        );
        assertEquals(
                "payment-timeout",
                document.metadata().section()
        );
        assertEquals(
                "2026-09-14",
                document.metadata().version()
        );
        assertEquals(
                DocumentVisibility.AUTHENTICATED,
                document.metadata().visibility()
        );
        assertEquals(
                "订单创建后30分钟内未支付会自动关闭。",
                document.content()
        );
    }

    @Test
    void shouldRejectMissingMetadataFields() {
        assertThrows(
                NullPointerException.class,
                () -> new KnowledgeDocumentMetadata(
                        null,
                        "payment-timeout",
                        "2026-09-14",
                        DocumentVisibility.PUBLIC
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeDocumentMetadata(
                        " ",
                        "payment-timeout",
                        "2026-09-14",
                        DocumentVisibility.PUBLIC
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeDocumentMetadata(
                        "knowledge/trading-rules.md",
                        " ",
                        "2026-09-14",
                        DocumentVisibility.PUBLIC
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeDocumentMetadata(
                        "knowledge/trading-rules.md",
                        "payment-timeout",
                        " ",
                        DocumentVisibility.PUBLIC
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new KnowledgeDocumentMetadata(
                        "knowledge/trading-rules.md",
                        "payment-timeout",
                        "2026-09-14",
                        null
                )
        );
    }

    @Test
    void shouldRejectMissingOrBlankDocumentContent() {
        KnowledgeDocumentMetadata metadata =
                validMetadata();

        assertThrows(
                NullPointerException.class,
                () -> new KnowledgeDocument(
                        null,
                        "有效正文"
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new KnowledgeDocument(
                        metadata,
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeDocument(
                        metadata,
                        "   \n\t"
                )
        );
    }

    @Test
    void shouldExposeThreeExplicitVisibilityLevels() {
        assertEquals(
                3,
                DocumentVisibility.values().length
        );
        assertEquals(
                DocumentVisibility.PUBLIC,
                DocumentVisibility.valueOf("PUBLIC")
        );
        assertEquals(
                DocumentVisibility.AUTHENTICATED,
                DocumentVisibility.valueOf("AUTHENTICATED")
        );
        assertEquals(
                DocumentVisibility.ADMIN_ONLY,
                DocumentVisibility.valueOf("ADMIN_ONLY")
        );
    }

    private KnowledgeDocumentMetadata validMetadata() {
        return new KnowledgeDocumentMetadata(
                "knowledge/faq.md",
                "order-query",
                "2026-09-14",
                DocumentVisibility.PUBLIC
        );
    }
}
