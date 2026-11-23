package com.codesprint.lesson0831.rag.answer;

import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.codesprint.lesson0831.rag.retrieval.RetrievedChunk;

import java.util.Objects;

public record RagCitation(
        String chunkId,
        String source,
        String section,
        String version
) {

    public RagCitation {
        chunkId = requireText(chunkId, "引用的Chunk ID不能为空");
        source = requireText(source, "引用的文档来源不能为空");
        section = requireText(section, "引用的章节不能为空");
        version = requireText(version, "引用的版本不能为空");
    }

    public static RagCitation from(RetrievedChunk retrievedChunk) {
        Objects.requireNonNull(
                retrievedChunk,
                "召回结果不能为空"
        );

        KnowledgeChunk chunk = retrievedChunk.chunk();
        KnowledgeDocumentMetadata metadata = chunk.metadata();

        return new RagCitation(
                chunk.id(),
                metadata.source(),
                metadata.section(),
                metadata.version()
        );
    }

    private static String requireText(
            String value,
            String message
    ) {
        Objects.requireNonNull(value, message);

        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }
}