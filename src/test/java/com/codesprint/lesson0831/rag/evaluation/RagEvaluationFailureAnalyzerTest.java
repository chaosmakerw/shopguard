package com.codesprint.lesson0831.rag.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagEvaluationFailureAnalyzerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void distinguishesExpectedSourceBelowGateFromUnsupportedHit() throws Exception {
        JsonNode report = mapper.readTree("""
                {
                  "configuration": {"minScore": 0.55},
                  "results": [
                    {
                      "id": "H1", "category": "NORMAL", "executionStatus": "COMPLETED",
                      "executionMode": "RAG", "expectedRetrieval": "HIT",
                      "expectedSource": "rules.md", "expectedSection": "refund",
                      "retrievalCode": "LOW_RELEVANCE", "retrieved": [],
                      "rawTopKCandidates": [
                        {"source": "rules.md", "section": "refund", "score": 0.42}
                      ]
                    },
                    {
                      "id": "N1", "category": "NO_ANSWER", "executionStatus": "COMPLETED",
                      "executionMode": "RAG", "expectedRetrieval": "NO_RELEVANT_HIT",
                      "retrievalCode": "SUCCESS",
                      "retrieved": [{"source": "faq.md", "score": 0.60}]
                    }
                  ]
                }
                """);

        JsonNode analysis = new RagEvaluationFailureAnalyzer(mapper).analyze(report);

        assertEquals(2, analysis.path("failedCaseCount").asInt());
        assertEquals("EXPECTED_SOURCE_BELOW_SCORE_GATE",
                analysis.path("failedCases").path(0).path("classification").asText());
        assertEquals(0.42,
                analysis.path("failedCases").path(0).path("expectedSourceScore").asDouble());
        assertEquals("UNSUPPORTED_SOURCE_PASSED_GATE",
                analysis.path("failedCases").path(1).path("classification").asText());
        assertEquals("NOT_CONFIRMED", analysis.path("rootCauseStatus").asText());
    }
}
