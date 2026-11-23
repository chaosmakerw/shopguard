package com.codesprint.lesson0831.rag.ingestion;

import java.util.List;
import java.util.Objects;

public class KnowledgeDocumentFormatValidator {

    public KnowledgeDocument validate(
            KnowledgeDocument document
    ) {
        Objects.requireNonNull(
                document,
                "待校验文档不能为空"
        );

        document.content().codePoints()
                .forEach(codePoint -> {
                    if (isForbidden(codePoint)) {
                        throw new IllegalArgumentException(
                                "文档包含非法控制字符或乱码替换符: "
                                        + document.metadata().source()
                        );
                    }
                });

        return document;
    }

    public List<KnowledgeDocument> validateAll(
            List<KnowledgeDocument> documents
    ) {
        Objects.requireNonNull(
                documents,
                "待校验文档列表不能为空"
        );

        if (documents.isEmpty()) {
            throw new IllegalArgumentException(
                    "待校验文档列表不能为空"
            );
        }

        List<KnowledgeDocument> sourceDocuments =
                List.copyOf(documents);

        for (KnowledgeDocument document
                : sourceDocuments) {
            validate(document);
        }

        return sourceDocuments;
    }

    private boolean isForbidden(int codePoint) {
        if (codePoint == 0xFFFD) {
            return true;
        }

        return Character.isISOControl(codePoint)
                && codePoint != '\n'
                && codePoint != '\r'
                && codePoint != '\t';
    }
}
