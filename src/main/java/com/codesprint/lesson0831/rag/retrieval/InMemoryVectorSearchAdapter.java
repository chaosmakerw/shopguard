package com.codesprint.lesson0831.rag.retrieval;

import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.EmbeddedChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.codesprint.lesson0831.rag.ingestion.VectorStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class InMemoryVectorSearchAdapter
        implements VectorSearchPort {

    private final VectorStore vectorStore;

    public InMemoryVectorSearchAdapter(
            VectorStore vectorStore
    ) {
        this.vectorStore = Objects.requireNonNull(
                vectorStore,
                "向量库不能为空"
        );
    }

    @Override
    public List<RetrievedChunk> search(
            List<Double> queryVector,
            String version,
            Set<DocumentVisibility> allowedVisibilities,
            int topK
    ) {
        validateSearchArguments(
                queryVector,
                version,
                allowedVisibilities,
                topK
        );

        List<EmbeddedChunk> storedChunks =
                vectorStore.findAll();

        List<RetrievedChunk> candidates =
                new ArrayList<>();

        for (EmbeddedChunk embeddedChunk
                : storedChunks) {
            KnowledgeDocumentMetadata metadata =
                    embeddedChunk.chunk().metadata();

            if (!version.equals(
                    metadata.version()
            )) {
                continue;
            }

            if (!allowedVisibilities.contains(
                    metadata.visibility()
            )) {
                continue;
            }

            double score = cosineSimilarity(
                    queryVector,
                    embeddedChunk.vector()
            );

            candidates.add(
                    new RetrievedChunk(
                            embeddedChunk.chunk(),
                            score
                    )
            );
        }

        candidates.sort(
                Comparator.comparingDouble(
                                RetrievedChunk::score
                        )
                        .reversed()
                        .thenComparing(result ->
                                result.chunk().id())
        );

        int resultSize = Math.min(
                topK,
                candidates.size()
        );

        return List.copyOf(
                candidates.subList(
                        0,
                        resultSize
                )
        );
    }

    private void validateSearchArguments(
            List<Double> queryVector,
            String version,
            Set<DocumentVisibility> allowedVisibilities,
            int topK
    ) {
        Objects.requireNonNull(
                queryVector,
                "查询向量不能为空"
        );

        if (queryVector.isEmpty()) {
            throw new IllegalArgumentException(
                    "查询向量不能为空"
            );
        }

        for (Double value : queryVector) {
            if (value == null
                    || !Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "查询向量不能包含空值或非有限数字"
                );
            }
        }

        requireText(
                version,
                "检索版本不能为空"
        );

        Objects.requireNonNull(
                allowedVisibilities,
                "允许访问的文档范围不能为空"
        );

        if (allowedVisibilities.isEmpty()) {
            throw new IllegalArgumentException(
                    "允许访问的文档范围不能为空"
            );
        }

        if (topK <= 0) {
            throw new IllegalArgumentException(
                    "topK必须大于0"
            );
        }
    }

    private double cosineSimilarity(
            List<Double> left,
            List<Double> right
    ) {
        if (left.size() != right.size()) {
            throw new IllegalArgumentException(
                    "查询向量与Chunk向量维度不一致"
            );
        }

        double dotProduct = 0.0;
        double leftSquaredLength = 0.0;
        double rightSquaredLength = 0.0;

        for (int index = 0;
             index < left.size();
             index++) {
            double leftValue =
                    left.get(index);

            double rightValue =
                    right.get(index);

            dotProduct =
                    dotProduct
                            + leftValue * rightValue;

            leftSquaredLength =
                    leftSquaredLength
                            + leftValue * leftValue;

            rightSquaredLength =
                    rightSquaredLength
                            + rightValue * rightValue;
        }

        if (leftSquaredLength == 0.0
                || rightSquaredLength == 0.0) {
            return 0.0;
        }

        return dotProduct
                / (Math.sqrt(leftSquaredLength)
                * Math.sqrt(rightSquaredLength));
    }

    private String requireText(
            String value,
            String message
    ) {
        Objects.requireNonNull(
                value,
                message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    message
            );
        }

        return value;
    }
}