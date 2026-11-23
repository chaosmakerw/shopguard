package com.codesprint.lesson0831.rag.retrieval;

import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.codesprint.lesson0831.rag.ingestion.TextEmbeddingModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CharacterBigramHybridRerankerTest {

    private final CharacterBigramHybridReranker reranker =
            new CharacterBigramHybridReranker();

    @Test
    void matchingEvidenceCanOutrankHigherVectorSimilarityWithoutChangingMetadata() {
        RetrievedChunk unrelated = chunk("trading", "未支付订单可以直接取消。", 0.45);
        RetrievedChunk relevant = chunk("refund", "已发货订单不能直接取消，先申请退货。", 0.43);

        List<RetrievedChunk> result = reranker.rerank(
                "已经发货的订单能直接取消吗？", List.of(unrelated, relevant));

        assertEquals("refund", result.get(0).chunk().id());
        assertSame(relevant.chunk(), result.get(0).chunk());
        assertEquals(2, result.size());
        assertEquals(0.43, relevant.score());
        assertThrows(UnsupportedOperationException.class,
                () -> result.add(unrelated));
    }

    @Test
    void unrelatedHighVectorCandidateCanBeRejectedByHybridThreshold() {
        RetrievedChunk unrelated = chunk("faq", "订单为空时返回空列表。", 0.57);
        TextEmbeddingModel embedding = text -> List.of(1.0);
        VectorSearchPort port = (vector, version, visibility, topK) -> List.of(unrelated);
        RetrievalService service = new RetrievalService(embedding, port, reranker);

        RetrievalResult result = service.retrieve(new RetrievalRequest(
                "周末人工客服的上班时间是几点？", "v1",
                Set.of(DocumentVisibility.PUBLIC), 3, 0.40));

        assertEquals(RetrievalCode.LOW_RELEVANCE, result.code());
    }

    @Test
    void rejectsNullCandidateWithoutInventingEvidence() {
        assertThrows(NullPointerException.class,
                () -> reranker.rerank("退款", java.util.Arrays.asList((RetrievedChunk) null)));
    }

    private RetrievedChunk chunk(String id, String text, double vectorScore) {
        KnowledgeChunk chunk = new KnowledgeChunk(id, 0, text,
                new KnowledgeDocumentMetadata("knowledge/" + id + ".md",
                        "rules", "v1", DocumentVisibility.PUBLIC));
        return new RetrievedChunk(chunk, vectorScore);
    }
}
