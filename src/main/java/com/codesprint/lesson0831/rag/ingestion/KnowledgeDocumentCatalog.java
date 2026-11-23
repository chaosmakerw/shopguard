package com.codesprint.lesson0831.rag.ingestion;

import java.util.List;

public final class KnowledgeDocumentCatalog {

    private static final String CURRENT_VERSION =
            "2026-09-14";

    private static final List<KnowledgeDocumentMetadata>
            STANDARD_METADATA =
            List.of(
                    new KnowledgeDocumentMetadata(
                            "knowledge/trading-rules.md",
                            "order-lifecycle",
                            CURRENT_VERSION,
                            DocumentVisibility.AUTHENTICATED
                    ),
                    new KnowledgeDocumentMetadata(
                            "knowledge/refund-policy.md",
                            "refund-boundaries",
                            CURRENT_VERSION,
                            DocumentVisibility.AUTHENTICATED
                    ),
                    new KnowledgeDocumentMetadata(
                            "knowledge/security-guide.md",
                            "agent-security",
                            CURRENT_VERSION,
                            DocumentVisibility.ADMIN_ONLY
                    ),
                    new KnowledgeDocumentMetadata(
                            "knowledge/faq.md",
                            "common-questions",
                            CURRENT_VERSION,
                            DocumentVisibility.PUBLIC
                    )
            );

    private KnowledgeDocumentCatalog() {
    }

    public static List<KnowledgeDocumentMetadata>
    standardMetadata() {
        return STANDARD_METADATA;
    }
}