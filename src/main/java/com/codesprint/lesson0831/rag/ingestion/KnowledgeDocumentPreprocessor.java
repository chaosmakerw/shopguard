package com.codesprint.lesson0831.rag.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KnowledgeDocumentPreprocessor {

    public KnowledgeDocument preprocess(
            KnowledgeDocument document
    ) {
        Objects.requireNonNull(
                document,
                "待预处理文档不能为空"
        );

        String normalizedContent = document.content()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll(
                        "(?m)^[\\t ]*#{1,6}[\\t ]+",
                        ""
                )
                .replaceAll(
                        "(?m)[\\t ]+$",
                        ""
                )
                .replaceAll(
                        "\\n{3,}",
                        "\n\n"
                )
                .strip();

        return new KnowledgeDocument(
                document.metadata(),
                normalizedContent
        );
    }

    public List<KnowledgeDocument> preprocessAll(
            List<KnowledgeDocument> documents
    ) {
        Objects.requireNonNull(
                documents,
                "待预处理文档列表不能为空"
        );

        if (documents.isEmpty()) {
            throw new IllegalArgumentException(
                    "待预处理文档列表不能为空"
            );
        }

        List<KnowledgeDocument> sourceDocuments =
                List.copyOf(documents);

        List<KnowledgeDocument> results =
                new ArrayList<>(
                        sourceDocuments.size()
                );

        for (KnowledgeDocument document
                : sourceDocuments) {
            results.add(preprocess(document));
        }

        return List.copyOf(results);
    }
}