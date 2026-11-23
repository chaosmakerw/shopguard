package com.codesprint.lesson0831.rag.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ClasspathKnowledgeDocumentLoader {

    private final ClassLoader classLoader;

    public ClasspathKnowledgeDocumentLoader() {
        this.classLoader =
                ClasspathKnowledgeDocumentLoader.class
                        .getClassLoader();
    }

    public KnowledgeDocument load(
            KnowledgeDocumentMetadata metadata
    ) {
        Objects.requireNonNull(
                metadata,
                "文档元数据不能为空"
        );

        try (
                InputStream inputStream =
                        classLoader.getResourceAsStream(
                                metadata.source()
                        )
        ) {
            if (inputStream == null) {
                throw new IllegalArgumentException(
                        "知识文档不存在: "
                                + metadata.source()
                );
            }

            String content =
                    new String(
                            inputStream.readAllBytes(),
                            StandardCharsets.UTF_8
                    );

            return new KnowledgeDocument(
                    metadata,
                    content
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "知识文档读取失败: "
                            + metadata.source(),
                    exception
            );
        }
    }

    public List<KnowledgeDocument> loadAll(
            List<KnowledgeDocumentMetadata> metadataList
    ) {
        Objects.requireNonNull(
                metadataList,
                "文档元数据列表不能为空"
        );

        List<KnowledgeDocumentMetadata> safeMetadata =
                List.copyOf(metadataList);

        if (safeMetadata.isEmpty()) {
            throw new IllegalArgumentException(
                    "文档元数据列表不能为空"
            );
        }

        List<KnowledgeDocument> documents =
                new ArrayList<>();

        for (KnowledgeDocumentMetadata metadata
                : safeMetadata) {
            documents.add(
                    load(metadata)
            );
        }

        return List.copyOf(documents);
    }
}