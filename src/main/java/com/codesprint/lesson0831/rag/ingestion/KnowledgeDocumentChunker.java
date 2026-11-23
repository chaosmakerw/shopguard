package com.codesprint.lesson0831.rag.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KnowledgeDocumentChunker {

    private final int chunkSize;
    private final int overlap;

    public KnowledgeDocumentChunker(
            int chunkSize,
            int overlap
    ) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "chunkSize必须大于0"
            );
        }

        if (overlap < 0) {
            throw new IllegalArgumentException(
                    "overlap不能小于0"
            );
        }

        if (overlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "overlap必须小于chunkSize"
            );
        }

        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<KnowledgeChunk> chunk(
            KnowledgeDocument document
    ) {
        Objects.requireNonNull(
                document,
                "待切分文档不能为空"
        );

        String content = document.content();
        int step = chunkSize - overlap;
        int start = 0;
        int chunkIndex = 0;

        List<KnowledgeChunk> chunks =
                new ArrayList<>();

        while (start < content.length()) {
            int end = Math.min(
                    start + chunkSize,
                    content.length()
            );

            String chunkText = content
                    .substring(start, end)
                    .strip();

            if (!chunkText.isBlank()) {
                String chunkId = createChunkId(
                        document.metadata(),
                        chunkIndex
                );

                chunks.add(
                        new KnowledgeChunk(
                                chunkId,
                                chunkIndex,
                                chunkText,
                                document.metadata()
                        )
                );

                chunkIndex++;
            }

            if (end == content.length()) {
                break;
            }

            start = start + step;
        }

        return List.copyOf(chunks);
    }

    public List<KnowledgeChunk> chunkAll(
            List<KnowledgeDocument> documents
    ) {
        Objects.requireNonNull(
                documents,
                "待切分文档列表不能为空"
        );

        if (documents.isEmpty()) {
            throw new IllegalArgumentException(
                    "待切分文档列表不能为空"
            );
        }

        List<KnowledgeDocument> sourceDocuments =
                List.copyOf(documents);

        List<KnowledgeChunk> allChunks =
                new ArrayList<>();

        for (KnowledgeDocument document
                : sourceDocuments) {
            allChunks.addAll(
                    chunk(document)
            );
        }

        return List.copyOf(allChunks);
    }

    private String createChunkId(
            KnowledgeDocumentMetadata metadata,
            int chunkIndex
    ) {
        return metadata.source()
                + "::"
                + metadata.version()
                + "::"
                + chunkIndex;
    }
}