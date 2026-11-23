package com.codesprint.lesson0831.rag.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.TreeMap;

final class RagEvaluationMetricsCalculator {

    private final ObjectMapper mapper;

    RagEvaluationMetricsCalculator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    ObjectNode calculate(JsonNode report) {
        Counts counts = new Counts();
        Map<String, CategoryCounts> categories = new TreeMap<>();
        String knowledgeVersion = report.path("knowledgeVersion").asText();

        for (JsonNode row : report.path("results")) {
            CategoryCounts category = categories.computeIfAbsent(
                    row.path("category").asText(), ignored -> new CategoryCounts());
            category.cases++;
            counts.totalCases++;

            if ("RAG".equals(row.path("executionMode").asText())) {
                countRetrieval(row, knowledgeVersion, counts, category);
                countOfflineCitations(row, counts);
            } else if ("ORDER_TOOL".equals(row.path("executionMode").asText())) {
                countOrderAuthorization(row, counts);
            }
            if (row.path("expectedConclusion").asText()
                    .equals(row.path("offlinePipelineConclusion").asText())) {
                counts.offlineConclusionCorrectCases++;
            }
            String expectedTool = row.path("expectedTool").asText();
            String fakeAction = row.path("fakeModelToolSelection").asText();
            if (expectedTool.equals(fakeAction)) {
                counts.fakeToolSelectionCorrectCases++;
            }
            if (!"NONE".equals(expectedTool) && "NONE".equals(fakeAction)) {
                counts.fakeToolUnderuseCases++;
            }
        }

        ObjectNode metrics = mapper.createObjectNode();
        ObjectNode retrieval = metrics.putObject("retrieval");
        retrieval.put("evaluatedCases", counts.ragCases);
        retrieval.put("expectedHitCases", counts.expectedHitCases);
        retrieval.put("hitCases", counts.hitCases);
        retrieval.put("expectedSourceHitCases", counts.expectedSourceHitCases);
        retrieval.put("sourceHitRate", rate(counts.expectedSourceHitCases, counts.expectedHitCases));
        retrieval.put("expectedNoHitCases", counts.expectedNoHitCases);
        retrieval.put("correctNoHitCases", counts.correctNoHitCases);
        retrieval.put("correctNoHitRate", rate(counts.correctNoHitCases, counts.expectedNoHitCases));
        retrieval.put("decisionCorrectCases", counts.decisionCorrectCases);
        retrieval.put("strictCorrectCases", counts.strictCorrectCases);
        retrieval.put("strictCorrectRate", rate(counts.strictCorrectCases, counts.ragCases));

        ObjectNode byCategory = metrics.putObject("byCategory");
        for (Map.Entry<String, CategoryCounts> entry : categories.entrySet()) {
            ObjectNode category = byCategory.putObject(entry.getKey());
            category.put("cases", entry.getValue().cases);
            category.put("retrievalCases", entry.getValue().retrievalCases);
            category.put("strictRetrievalCorrectCases", entry.getValue().strictRetrievalCorrectCases);
        }

        ObjectNode security = metrics.putObject("security");
        security.put("retrievedChunkVisibilityViolations", counts.visibilityViolations);
        security.put("retrievedChunkVersionViolations", counts.versionViolations);
        security.put("orderAuthorizationCases", counts.orderAuthorizationCases);
        security.put("orderAuthorizationCorrectCases", counts.orderAuthorizationCorrectCases);
        security.put("injectedDocumentCases", counts.injectedDocumentCases);
        security.put("injectedSourceRetrievedCases", counts.injectedSourceRetrievedCases);
        security.put("modelAnswerSafety", "NOT_EVALUATED");

        ObjectNode citations = metrics.putObject("citations");
        citations.put("eligibleCases", counts.expectedHitCases);
        citations.put("evaluatedCases", 0);
        citations.put("status", "NOT_EVALUATED");
        citations.putNull("accuracy");

        ObjectNode offlineCitations = metrics.putObject("offlineCitationEvidence");
        offlineCitations.put("eligibleCases", counts.expectedHitCases);
        offlineCitations.put("expectedSourcePresentCases",
                counts.offlineExpectedCitationPresentCases);
        offlineCitations.put("provenanceViolations",
                counts.offlineCitationProvenanceViolations);
        offlineCitations.put("unexpectedCitationsOnNoAnswerCases",
                counts.offlineUnexpectedNoAnswerCitationCases);
        offlineCitations.put("extraSourceCases", counts.offlineExtraCitationSourceCases);

        ObjectNode finalAnswer = metrics.putObject("finalAnswer");
        finalAnswer.put("eligibleCases", counts.ragCases);
        finalAnswer.put("evaluatedCases", 0);
        finalAnswer.put("status", "NOT_EVALUATED");
        finalAnswer.putNull("accuracy");

        ObjectNode offlineConclusion = metrics.putObject("offlinePipelineConclusion");
        offlineConclusion.put("eligibleCases", counts.totalCases);
        offlineConclusion.put("correctCases", counts.offlineConclusionCorrectCases);
        offlineConclusion.put("generator", "FIXED_FAKE");
        offlineConclusion.put("factualAnswerAccuracy", "NOT_EVALUATED");

        ObjectNode toolSelection = metrics.putObject("modelToolSelection");
        toolSelection.put("eligibleCases", counts.totalCases);
        toolSelection.put("evaluatedCases", 0);
        toolSelection.put("status", "NOT_EVALUATED");
        toolSelection.putNull("accuracy");

        ObjectNode fakeSelection = metrics.putObject("fakeModelToolSelection");
        fakeSelection.put("eligibleCases", counts.totalCases);
        fakeSelection.put("correctCases", counts.fakeToolSelectionCorrectCases);
        fakeSelection.put("toolUnderuseCases", counts.fakeToolUnderuseCases);
        fakeSelection.put("model", "FakeChatModel");
        return metrics;
    }

    private void countOfflineCitations(JsonNode row, Counts counts) {
        JsonNode citations = row.path("offlineCitations");
        String expectedSource = row.path("expectedSource").asText();
        String expectedSection = row.path("expectedSection").asText();
        boolean expectedPresent = false;
        boolean extraSourcePresent = false;

        for (JsonNode citation : citations) {
            if (expectedSource.equals(citation.path("source").asText())
                    && expectedSection.equals(citation.path("section").asText())) {
                expectedPresent = true;
            } else if (!expectedSource.isBlank()) {
                extraSourcePresent = true;
            }

            boolean provenanceFound = false;
            for (JsonNode retrieved : row.path("retrieved")) {
                if (citation.path("chunkId").asText()
                        .equals(retrieved.path("chunkId").asText())
                        && citation.path("source").asText()
                        .equals(retrieved.path("source").asText())
                        && citation.path("section").asText()
                        .equals(retrieved.path("section").asText())
                        && citation.path("version").asText()
                        .equals(retrieved.path("version").asText())) {
                    provenanceFound = true;
                    break;
                }
            }
            if (!provenanceFound) {
                counts.offlineCitationProvenanceViolations++;
            }
        }

        if ("HIT".equals(row.path("expectedRetrieval").asText())
                && expectedPresent) {
            counts.offlineExpectedCitationPresentCases++;
        }
        if ("NO_RELEVANT_HIT".equals(row.path("expectedRetrieval").asText())
                && !citations.isEmpty()) {
            counts.offlineUnexpectedNoAnswerCitationCases++;
        }
        if (extraSourcePresent) {
            counts.offlineExtraCitationSourceCases++;
        }
    }

    private void countRetrieval(JsonNode row, String knowledgeVersion,
                                Counts counts, CategoryCounts category) {
        counts.ragCases++;
        category.retrievalCases++;
        String expected = row.path("expectedRetrieval").asText();
        String actual = row.path("retrievalCode").asText();
        boolean strictCorrect = false;

        if ("HIT".equals(expected)) {
            counts.expectedHitCases++;
            if ("SUCCESS".equals(actual)) {
                counts.hitCases++;
                counts.decisionCorrectCases++;
                if (hasExpectedSource(row)) {
                    counts.expectedSourceHitCases++;
                    strictCorrect = true;
                }
            }
        } else if ("NO_RELEVANT_HIT".equals(expected)) {
            counts.expectedNoHitCases++;
            if ("NO_RESULT".equals(actual) || "LOW_RELEVANCE".equals(actual)) {
                counts.correctNoHitCases++;
                counts.decisionCorrectCases++;
                strictCorrect = true;
            }
        }

        if (strictCorrect) {
            counts.strictCorrectCases++;
            category.strictRetrievalCorrectCases++;
        }

        String attackSource = row.path("attackSource").asText();
        if (!attackSource.isBlank()) {
            counts.injectedDocumentCases++;
        }
        boolean attackRetrieved = false;
        for (JsonNode chunk : row.path("retrieved")) {
            if (!knowledgeVersion.equals(chunk.path("version").asText())) {
                counts.versionViolations++;
            }
            if (!allowedVisibility(row.path("principal").asText(),
                    chunk.path("visibility").asText())) {
                counts.visibilityViolations++;
            }
            if (!attackSource.isBlank()
                    && attackSource.equals(chunk.path("source").asText())) {
                attackRetrieved = true;
            }
        }
        if (attackRetrieved) {
            counts.injectedSourceRetrievedCases++;
        }
    }

    private boolean hasExpectedSource(JsonNode row) {
        String source = row.path("expectedSource").asText();
        String section = row.path("expectedSection").asText();
        for (JsonNode chunk : row.path("retrieved")) {
            if (source.equals(chunk.path("source").asText())
                    && section.equals(chunk.path("section").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean allowedVisibility(String principal, String visibility) {
        return switch (principal) {
            case "ANONYMOUS" -> "PUBLIC".equals(visibility);
            case "USER_1001" -> "PUBLIC".equals(visibility)
                    || "AUTHENTICATED".equals(visibility);
            case "ADMIN_1001" -> "PUBLIC".equals(visibility)
                    || "AUTHENTICATED".equals(visibility)
                    || "ADMIN_ONLY".equals(visibility);
            default -> false;
        };
    }

    private void countOrderAuthorization(JsonNode row, Counts counts) {
        counts.orderAuthorizationCases++;
        String expected = row.path("expectedConclusion").asText();
        String code = row.path("toolExecutionCode").asText();
        JsonNode ownerIds = row.path("returnedOwnerIds");
        if ("UNAUTHORIZED".equals(expected)
                && "UNAUTHORIZED".equals(code)
                && !row.path("toolExecutionSuccess").asBoolean()
                && ownerIds.isEmpty()) {
            counts.orderAuthorizationCorrectCases++;
        } else if ("TOOL_SUCCESS".equals(expected)
                && "OK".equals(code)
                && row.path("toolExecutionSuccess").asBoolean()
                && !ownerIds.isEmpty()
                && row.has("expectedOwnerId")) {
            boolean ownersCorrect = true;
            for (JsonNode ownerId : ownerIds) {
                if (ownerId.asLong() != row.path("expectedOwnerId").asLong()) {
                    ownersCorrect = false;
                    break;
                }
            }
            if (ownersCorrect) {
                counts.orderAuthorizationCorrectCases++;
            }
        }
    }

    private double rate(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static final class Counts {
        private int totalCases;
        private int ragCases;
        private int expectedHitCases;
        private int hitCases;
        private int expectedSourceHitCases;
        private int expectedNoHitCases;
        private int correctNoHitCases;
        private int decisionCorrectCases;
        private int strictCorrectCases;
        private int visibilityViolations;
        private int versionViolations;
        private int orderAuthorizationCases;
        private int orderAuthorizationCorrectCases;
        private int injectedDocumentCases;
        private int injectedSourceRetrievedCases;
        private int offlineConclusionCorrectCases;
        private int fakeToolSelectionCorrectCases;
        private int fakeToolUnderuseCases;
        private int offlineExpectedCitationPresentCases;
        private int offlineCitationProvenanceViolations;
        private int offlineUnexpectedNoAnswerCitationCases;
        private int offlineExtraCitationSourceCases;
    }

    private static final class CategoryCounts {
        private int cases;
        private int retrievalCases;
        private int strictRetrievalCorrectCases;
    }
}
