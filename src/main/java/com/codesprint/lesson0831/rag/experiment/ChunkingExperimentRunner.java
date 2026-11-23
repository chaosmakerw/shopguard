package com.codesprint.lesson0831.rag.experiment;

import com.codesprint.lesson0831.rag.ingestion.EmbeddedChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunkEmbedder;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocument;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentChunker;
import com.codesprint.lesson0831.rag.ingestion.TextEmbeddingModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ChunkingExperimentRunner {

    private final TextEmbeddingModel embeddingModel;

    public ChunkingExperimentRunner(
            TextEmbeddingModel embeddingModel
    ) {
        this.embeddingModel = Objects.requireNonNull(
                embeddingModel,
                "实验Embedding模型不能为空"
        );
    }

    public ChunkingExperimentResult run(
            KnowledgeDocument document,
            String query,
            ChunkingExperimentConfiguration configuration,
            int topK
    ) {
        Objects.requireNonNull(
                document,
                "实验文档不能为空"
        );
        Objects.requireNonNull(
                query,
                "实验问题不能为空"
        );
        Objects.requireNonNull(
                configuration,
                "实验配置不能为空"
        );

        if (query.isBlank()) {
            throw new IllegalArgumentException(
                    "实验问题不能为空"
            );
        }

        if (topK <= 0) {
            throw new IllegalArgumentException(
                    "topK必须大于0"
            );
        }

        KnowledgeDocumentChunker chunker =
                new KnowledgeDocumentChunker(
                        configuration.chunkSize(),
                        configuration.overlap()
                );

        List<KnowledgeChunk> chunks =
                chunker.chunk(document);

        KnowledgeChunkEmbedder embedder =
                new KnowledgeChunkEmbedder(
                        embeddingModel
                );

        List<EmbeddedChunk> embeddedChunks =
                embedder.embedAll(chunks);

        List<Double> queryVector =
                embeddingModel.embed(query);

        List<ScoredChunk> scoredChunks =
                new ArrayList<>(
                        embeddedChunks.size()
                );

        for (EmbeddedChunk embeddedChunk
                : embeddedChunks) {
            double score = cosineSimilarity(
                    queryVector,
                    embeddedChunk.vector()
            );

            scoredChunks.add(
                    new ScoredChunk(
                            embeddedChunk.chunk(),
                            score
                    )
            );
        }

        scoredChunks.sort(
                Comparator.comparingDouble(
                                ScoredChunk::score
                        )
                        .reversed()
                        .thenComparingInt(scored ->
                                scored.chunk().index())
        );

        int resultSize = Math.min(
                topK,
                scoredChunks.size()
        );

        return new ChunkingExperimentResult(
                configuration,
                query,
                chunks.size(),
                scoredChunks.subList(
                        0,
                        resultSize
                )
        );
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
            double leftValue = left.get(index);
            double rightValue = right.get(index);

            dotProduct += leftValue * rightValue;
            leftSquaredLength += leftValue * leftValue;
            rightSquaredLength += rightValue * rightValue;
        }

        if (leftSquaredLength == 0.0
                || rightSquaredLength == 0.0) {
            return 0.0;
        }

        return dotProduct
                / (Math.sqrt(leftSquaredLength)
                * Math.sqrt(rightSquaredLength));
    }
}
