package com.codesprint.lesson0831.rag.retrieval;

import com.codesprint.lesson0831.rag.ingestion.TextEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.codesprint.lesson0831.observability.AgentOperationMetrics;

public class RetrievalService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    RetrievalService.class
            );

    private final TextEmbeddingModel embeddingModel;

    private final VectorSearchPort vectorSearchPort;

    private final RetrievalReranker reranker;

    private final AgentOperationMetrics metrics;
    public RetrievalService(
            TextEmbeddingModel embeddingModel,
            VectorSearchPort vectorSearchPort
    ) {
        this(
                embeddingModel,
                vectorSearchPort,
                null,
                AgentOperationMetrics.noop()
        );
    }

    public RetrievalService(
            TextEmbeddingModel embeddingModel,
            VectorSearchPort vectorSearchPort,
            RetrievalReranker reranker
    ) {
        this(
                embeddingModel,
                vectorSearchPort,
                reranker,
                AgentOperationMetrics.noop()
        );
    }

    public RetrievalService(
            TextEmbeddingModel embeddingModel,
            VectorSearchPort vectorSearchPort,
            RetrievalReranker reranker,
            AgentOperationMetrics metrics
    ) {
        this.embeddingModel =
                Objects.requireNonNull(
                        embeddingModel,
                        "Embedding模型不能为空"
                );

        this.vectorSearchPort =
                Objects.requireNonNull(
                        vectorSearchPort,
                        "向量检索端口不能为空"
                );

        this.reranker = reranker;

        this.metrics =
                Objects.requireNonNull(
                        metrics,
                        "指标组件不能为空"
                );
    }

    public RetrievalResult retrieve(
            RetrievalRequest request
    ) {
        Objects.requireNonNull(
                request,
                "检索请求不能为空"
        );
        long startNanos = System.nanoTime();
        List<Double> queryVector =
                embeddingModel.embed(
                        request.query()
                );

        List<RetrievedChunk> candidates;

        try {
            candidates = Objects.requireNonNull(
                    vectorSearchPort.search(
                            queryVector,
                            request.version(),
                            request.allowedVisibilities(),
                            request.topK()
                    ),
                    "向量检索端口不能返回null"
            );
        } catch (RuntimeException exception) {
            long elapsedNanos =
                    System.nanoTime() - startNanos;

            metrics.record(
                    "retrieval",
                    "VECTOR_STORE_UNAVAILABLE",
                    elapsedNanos
            );

            long latencyMs =
                    elapsedNanos / 1_000_000;

            log.warn(
                    "retrieval requestId={} stage=VECTOR_SEARCH latencyMs={} resultCode=VECTOR_STORE_UNAVAILABLE exceptionType={}",
                    currentRequestId(),
                    latencyMs,
                    exception.getClass().getName()
            );

            return RetrievalResult.storeUnavailable();
        }

        if (candidates.isEmpty()) {
            return recordResult(
                    startNanos,
                    RetrievalResult.noResult()
            );
        }

        List<RetrievedChunk> rankedCandidates = reranker == null
                ? candidates
                : Objects.requireNonNull(
                        reranker.rerank(request.query(), candidates),
                        "重排器不能返回null"
                );

        List<RetrievedChunk> relevantChunks =
                new ArrayList<>();

        for (RetrievedChunk candidate
                : rankedCandidates) {
            if (candidate.score()
                    >= request.minScore()) {
                relevantChunks.add(candidate);
            }
        }

        if (relevantChunks.isEmpty()) {
            return recordResult(
                    startNanos,
                    RetrievalResult.lowRelevance()
            );
        }

        return recordResult(
                startNanos,
                RetrievalResult.found(
                        relevantChunks
                )
        );
    }
    private RetrievalResult recordResult(
            long startNanos,
            RetrievalResult result
    ) {
        long elapsedNanos =
                System.nanoTime() - startNanos;

        metrics.record(
                "retrieval",
                result.code().name(),
                elapsedNanos
        );

        long latencyMs =
                elapsedNanos / 1_000_000;

        log.info(
                "retrieval requestId={} latencyMs={} resultCode={}",
                currentRequestId(),
                latencyMs,
                result.code()
        );

        return result;
    }

    private String currentRequestId() {
        String requestId = MDC.get("requestId");

        if (requestId == null || requestId.isBlank()) {
            return "UNAVAILABLE";
        }

        return requestId;
    }
}
