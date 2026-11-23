package com.codesprint.lesson0831.rag.answer;

import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunk;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.codesprint.lesson0831.rag.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagAnswerContractTest {

    @Test
    void citationShouldComeFromActualRetrievedChunkMetadata() {
        RagCitation citation = RagCitation.from(chunk("refund-v2", "v2", 0.9));

        assertEquals("refund-v2", citation.chunkId());
        assertEquals("knowledge/refund.md", citation.source());
        assertEquals("refund", citation.section());
        assertEquals("v2", citation.version());
    }

    @Test
    void citationShouldRejectMissingSourceAndNullChunk() {
        assertThrows(NullPointerException.class, () -> RagCitation.from(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RagCitation("id", " ", "refund", "v1")
        );
    }

    @Test
    void answerResultShouldKeepStableCodesAndCopyCitations() {
        List<RagCitation> source = new ArrayList<>();
        source.add(RagCitation.from(chunk("id", "v1", 0.9)));

        RagAnswerResult answered = RagAnswerResult.answered("需要先申请退货。", source);
        source.clear();

        assertTrue(answered.success());
        assertEquals(RagAnswerCode.ANSWERED, answered.code());
        assertEquals(1, answered.citations().size());
        assertThrows(UnsupportedOperationException.class, () -> answered.citations().clear());

        assertTrue(RagAnswerResult.insufficientEvidence().success());
        assertEquals(RagAnswerCode.INSUFFICIENT_EVIDENCE,
                RagAnswerResult.insufficientEvidence().code());
        assertFalse(RagAnswerResult.retrievalUnavailable().success());
        assertFalse(RagAnswerResult.generationUnavailable().success());
    }

    @Test
    void answerResultShouldRejectContradictoryStates() {
        RagCitation citation = RagCitation.from(chunk("id", "v1", 0.9));

        assertThrows(IllegalArgumentException.class,
                () -> RagAnswerResult.answered("答案", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new RagAnswerResult(false, RagAnswerCode.ANSWERED,
                        "答案", List.of(citation)));
        assertThrows(IllegalArgumentException.class,
                () -> new RagAnswerResult(true, RagAnswerCode.INSUFFICIENT_EVIDENCE,
                        "资料不足", List.of(citation)));
        assertThrows(IllegalArgumentException.class,
                () -> new RagAnswerResult(true, RagAnswerCode.GENERATION_UNAVAILABLE,
                        "故障", List.of()));
    }

    @Test
    void generationResultShouldDistinguishAnswerAndRefusal() {
        RagGenerationResult answered = RagGenerationResult.answered(
                "规则答案",
                List.of("refund-1")
        );
        RagGenerationResult refused = RagGenerationResult.noAnswer();

        assertTrue(answered.answered());
        assertEquals("规则答案", answered.answer());
        assertEquals(List.of("refund-1"), answered.citedChunkIds());
        assertFalse(refused.answered());
        assertEquals("", refused.answer());
        assertTrue(refused.citedChunkIds().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> RagGenerationResult.answered(
                        " ",
                        List.of("refund-1")
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new RagGenerationResult(
                        false,
                        "不应存在的答案",
                        List.of()
                ));
        assertThrows(IllegalArgumentException.class,
                () -> RagGenerationResult.answered(
                        "规则答案",
                        List.of()
                ));
    }

    private RetrievedChunk chunk(String id, String version, double score) {
        return new RetrievedChunk(
                new KnowledgeChunk(id, 0, "退款正文",
                        new KnowledgeDocumentMetadata("knowledge/refund.md",
                                "refund", version, DocumentVisibility.PUBLIC)),
                score
        );
    }
}
