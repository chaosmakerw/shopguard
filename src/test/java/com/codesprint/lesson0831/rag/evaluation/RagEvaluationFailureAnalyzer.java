package com.codesprint.lesson0831.rag.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class RagEvaluationFailureAnalyzer {

    private final ObjectMapper mapper;

    RagEvaluationFailureAnalyzer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    ObjectNode analyze(JsonNode report) {
        ObjectNode analysis = mapper.createObjectNode();
        ArrayNode failures = analysis.putArray("failedCases");
        double minScore = report.path("configuration").path("minScore").asDouble();

        for (JsonNode row : report.path("results")) {
            if (!"COMPLETED".equals(row.path("executionStatus").asText())) {
                addFailure(failures, row, "EXECUTION", "EXECUTION_ERROR");
                continue;
            }
            if ("RAG".equals(row.path("executionMode").asText())) {
                analyzeRetrieval(row, minScore, failures);
            } else if ("ORDER_TOOL".equals(row.path("executionMode").asText())) {
                analyzeOrderTool(row, failures);
            }
        }

        analysis.put("failedCaseCount", failures.size());
        analysis.put("rootCauseStatus", "NOT_CONFIRMED");
        analysis.put("finalAnswerFailureStatus", "NOT_EVALUATED");
        return analysis;
    }

    private void analyzeRetrieval(JsonNode row, double minScore, ArrayNode failures) {
        String expected = row.path("expectedRetrieval").asText();
        String actual = row.path("retrievalCode").asText();

        if ("HIT".equals(expected)) {
            if (containsExpectedSource(row.path("retrieved"), row)) {
                return;
            }
            if ("SUCCESS".equals(actual)) {
                addFailure(failures, row, "RETRIEVAL", "WRONG_SOURCE_RETRIEVED");
                return;
            }

            JsonNode candidates = row.has("preGateCandidates")
                    ? row.path("preGateCandidates") : row.path("rawTopKCandidates");
            JsonNode rawSource = findExpectedSource(candidates, row);
            if (rawSource != null) {
                ObjectNode failure = addFailure(failures, row, "RETRIEVAL",
                        "EXPECTED_SOURCE_BELOW_SCORE_GATE");
                failure.put("expectedSourceScore", rawSource.path("score").asDouble());
                failure.put("minScore", minScore);
            } else {
                addFailure(failures, row, "RETRIEVAL",
                        "EXPECTED_SOURCE_NOT_IN_TOP_K");
            }
        } else if ("NO_RELEVANT_HIT".equals(expected)
                && "SUCCESS".equals(actual)) {
            ObjectNode failure = addFailure(failures, row, "RETRIEVAL",
                    "UNSUPPORTED_SOURCE_PASSED_GATE");
            JsonNode first = row.path("retrieved").path(0);
            if (!first.isMissingNode()) {
                failure.put("topSource", first.path("source").asText());
                failure.put("topScore", first.path("score").asDouble());
                failure.put("minScore", minScore);
            }
        } else if ("HIT".equals(expected)
                || "NO_RELEVANT_HIT".equals(expected)) {
            if (!"NO_RESULT".equals(actual)
                    && !"LOW_RELEVANCE".equals(actual)) {
                addFailure(failures, row, "RETRIEVAL", "RETRIEVAL_ERROR");
            }
        }
    }

    private JsonNode findExpectedSource(JsonNode candidates, JsonNode row) {
        String source = row.path("expectedSource").asText();
        String section = row.path("expectedSection").asText();
        for (JsonNode candidate : candidates) {
            if (source.equals(candidate.path("source").asText())
                    && section.equals(candidate.path("section").asText())) {
                return candidate;
            }
        }
        return null;
    }

    private boolean containsExpectedSource(JsonNode candidates, JsonNode row) {
        return findExpectedSource(candidates, row) != null;
    }

    private void analyzeOrderTool(JsonNode row, ArrayNode failures) {
        String expected = row.path("expectedConclusion").asText();
        String code = row.path("toolExecutionCode").asText();
        JsonNode owners = row.path("returnedOwnerIds");
        if ("UNAUTHORIZED".equals(expected)) {
            if (!"UNAUTHORIZED".equals(code)
                    || row.path("toolExecutionSuccess").asBoolean()
                    || !owners.isEmpty()) {
                addFailure(failures, row, "TOOL_AUTHORIZATION", "ANONYMOUS_ORDER_ACCESS");
            }
        } else if ("TOOL_SUCCESS".equals(expected)) {
            boolean valid = "OK".equals(code)
                    && row.path("toolExecutionSuccess").asBoolean()
                    && !owners.isEmpty()
                    && row.has("expectedOwnerId");
            for (JsonNode owner : owners) {
                valid &= owner.asLong() == row.path("expectedOwnerId").asLong();
            }
            if (!valid) {
                addFailure(failures, row, "TOOL_AUTHORIZATION", "ORDER_OWNER_MISMATCH");
            }
        }
    }

    private ObjectNode addFailure(ArrayNode failures, JsonNode row,
                                  String stage, String classification) {
        ObjectNode failure = failures.addObject();
        failure.put("id", row.path("id").asText());
        failure.put("category", row.path("category").asText());
        failure.put("stage", stage);
        failure.put("classification", classification);
        failure.put("expectedRetrieval", row.path("expectedRetrieval").asText());
        failure.put("actualRetrieval", row.path("retrievalCode").asText());
        failure.put("expectedSource", row.path("expectedSource").asText());
        return failure;
    }
}
