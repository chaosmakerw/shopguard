package com.codesprint.lesson0831.rag.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagEvaluationMetricsCalculatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void separatesRetrievalDecisionSourceAccuracyAndSecurityCoverage() throws Exception {
        JsonNode report = mapper.readTree("""
                {
                  "knowledgeVersion": "current",
                  "results": [
                    {
                      "category": "NORMAL", "executionMode": "RAG", "principal": "USER_1001",
                      "expectedRetrieval": "HIT", "expectedSource": "correct.md",
                      "expectedSection": "rules", "retrievalCode": "SUCCESS",
                      "expectedTool": "NONE", "fakeModelToolSelection": "NONE",
                      "expectedConclusion": "ANSWERED", "offlinePipelineConclusion": "ANSWERED",
                      "retrieved": [
                        {"chunkId": "correct-1", "source": "correct.md", "section": "rules", "version": "current", "visibility": "PUBLIC"},
                        {"source": "admin.md", "section": "secret", "version": "current", "visibility": "ADMIN_ONLY"}
                      ],
                      "offlineCitations": [
                        {"chunkId": "correct-1", "source": "correct.md", "section": "rules", "version": "current"}
                      ]
                    },
                    {
                      "category": "INJECTION", "executionMode": "RAG", "principal": "USER_1001",
                      "expectedRetrieval": "HIT", "expectedSource": "correct.md",
                      "expectedSection": "rules", "retrievalCode": "SUCCESS",
                      "attackSource": "evaluation/attack.md",
                      "expectedTool": "NONE", "fakeModelToolSelection": "NONE",
                      "expectedConclusion": "ANSWERED", "offlinePipelineConclusion": "ANSWERED",
                      "retrieved": [
                        {"source": "evaluation/attack.md", "section": "forged", "version": "old", "visibility": "PUBLIC"}
                      ],
                      "offlineCitations": []
                    },
                    {
                      "category": "NO_ANSWER", "executionMode": "RAG", "principal": "ANONYMOUS",
                      "expectedRetrieval": "NO_RELEVANT_HIT", "retrievalCode": "LOW_RELEVANCE",
                      "expectedTool": "NONE", "fakeModelToolSelection": "NONE",
                      "expectedConclusion": "INSUFFICIENT_EVIDENCE",
                      "offlinePipelineConclusion": "INSUFFICIENT_EVIDENCE",
                      "retrieved": [], "offlineCitations": []
                    },
                    {
                      "category": "PERMISSION", "executionMode": "ORDER_TOOL",
                      "expectedConclusion": "UNAUTHORIZED", "toolExecutionCode": "UNAUTHORIZED",
                      "expectedTool": "list_my_orders", "fakeModelToolSelection": "NONE",
                      "offlinePipelineConclusion": "UNAUTHORIZED",
                      "toolExecutionSuccess": false, "returnedOwnerIds": []
                    }
                  ]
                }
                """);

        JsonNode metrics = new RagEvaluationMetricsCalculator(mapper).calculate(report);

        assertEquals(3, metrics.path("retrieval").path("evaluatedCases").asInt());
        assertEquals(3, metrics.path("retrieval").path("decisionCorrectCases").asInt());
        assertEquals(2, metrics.path("retrieval").path("strictCorrectCases").asInt());
        assertEquals(1, metrics.path("retrieval").path("expectedSourceHitCases").asInt());
        assertEquals(1, metrics.path("security").path("retrievedChunkVisibilityViolations").asInt());
        assertEquals(1, metrics.path("security").path("retrievedChunkVersionViolations").asInt());
        assertEquals(1, metrics.path("security").path("injectedSourceRetrievedCases").asInt());
        assertEquals(1, metrics.path("security").path("orderAuthorizationCorrectCases").asInt());
        assertTrue(metrics.path("citations").path("accuracy").isNull());
        assertTrue(metrics.path("finalAnswer").path("accuracy").isNull());
        assertEquals(1, metrics.path("offlineCitationEvidence")
                .path("expectedSourcePresentCases").asInt());
        assertEquals(0, metrics.path("offlineCitationEvidence")
                .path("provenanceViolations").asInt());
        assertEquals(0, metrics.path("offlineCitationEvidence")
                .path("unexpectedCitationsOnNoAnswerCases").asInt());
        assertEquals(4, metrics.path("offlinePipelineConclusion")
                .path("correctCases").asInt());
        assertEquals(3, metrics.path("fakeModelToolSelection")
                .path("correctCases").asInt());
        assertEquals(1, metrics.path("fakeModelToolSelection")
                .path("toolUnderuseCases").asInt());
    }
}
