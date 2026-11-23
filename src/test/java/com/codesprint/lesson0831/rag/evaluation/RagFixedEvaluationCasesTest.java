package com.codesprint.lesson0831.rag.evaluation;

import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentCatalog;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagFixedEvaluationCasesTest {

    private static final String RESOURCE = "rag/evaluation/fixed-cases-0919.json";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void containsFiveCategoriesWithFourStableCasesEach() throws IOException {
        JsonNode root = load();
        JsonNode cases = root.path("cases");

        assertEquals("2026-09-19-v1", root.path("datasetVersion").asText());
        assertEquals("2026-09-14", root.path("knowledgeVersion").asText());
        assertEquals(20, cases.size());

        Map<String, Integer> counts = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode item : cases) {
            String id = item.path("id").asText();
            String category = item.path("category").asText();
            assertFalse(id.isBlank());
            assertTrue(ids.add(id), "重复评测编号: " + id);
            counts.merge(category, 1, Integer::sum);
        }
        assertEquals(Map.of("NORMAL", 4, "NO_ANSWER", 4,
                "WRONG_RULE", 4, "INJECTION", 4, "PERMISSION", 4), counts);
    }

    @Test
    void expectedSourcesAndPermissionsMatchCurrentKnowledgeCatalog() throws IOException {
        Map<String, KnowledgeDocumentMetadata> catalog = new HashMap<>();
        for (KnowledgeDocumentMetadata metadata : KnowledgeDocumentCatalog.standardMetadata()) {
            catalog.put(metadata.source(), metadata);
        }

        for (JsonNode item : load().path("cases")) {
            String id = item.path("id").asText();
            String principal = item.path("principal").asText();
            String executionMode = item.path("executionMode").asText();
            String tool = item.path("expectedTool").asText();
            String retrieval = item.path("expectedRetrieval").asText();
            String conclusion = item.path("expectedConclusion").asText();

            assertFalse(item.path("question").asText().isBlank(), id);
            assertTrue(Set.of("ANONYMOUS", "USER_1001", "ADMIN_1001").contains(principal), id);
            assertTrue(Set.of("RAG", "ORDER_TOOL").contains(executionMode), id);
            assertTrue(Set.of("NONE", "list_my_orders").contains(tool), id);
            assertTrue(Set.of("HIT", "NO_RELEVANT_HIT", "NOT_APPLICABLE").contains(retrieval), id);
            assertTrue(Set.of("ANSWERED", "INSUFFICIENT_EVIDENCE",
                    "TOOL_SUCCESS", "UNAUTHORIZED").contains(conclusion), id);

            String expectedSource = item.path("expectedSource").asText();
            if (!expectedSource.isBlank()) {
                KnowledgeDocumentMetadata metadata = catalog.get(expectedSource);
                assertNotNull(metadata, id + " 的引用来源不在知识清单中");
                assertEquals(metadata.section(), item.path("expectedSection").asText(), id);
                if (principal.equals("ANONYMOUS")) {
                    assertEquals(DocumentVisibility.PUBLIC, metadata.visibility(), id);
                } else if (principal.equals("USER_1001")) {
                    assertFalse(metadata.visibility() == DocumentVisibility.ADMIN_ONLY, id);
                }
            }

            if (!tool.equals("NONE")) {
                assertEquals("ORDER_TOOL", executionMode, id);
                assertEquals("list_my_orders", item.path("requestedTool").asText(), id);
                assertEquals("NOT_APPLICABLE", retrieval, id);
                assertTrue(Set.of("TOOL_SUCCESS", "UNAUTHORIZED").contains(conclusion), id);
            } else if (conclusion.equals("ANSWERED")) {
                assertEquals("RAG", executionMode, id);
                assertEquals("HIT", retrieval, id);
                assertFalse(expectedSource.isBlank(), id);
                assertTrue(item.path("requiredFacts").isArray(), id);
                assertTrue(item.path("requiredFacts").size() > 0, id);
            } else {
                assertEquals("RAG", executionMode, id);
                assertEquals("INSUFFICIENT_EVIDENCE", conclusion, id);
                assertEquals("NO_RELEVANT_HIT", retrieval, id);
            }

            JsonNode attackDocument = item.path("attackDocument");
            if (!attackDocument.isMissingNode()) {
                assertEquals("INJECTION", item.path("category").asText(), id);
                assertTrue(attackDocument.path("source").asText().startsWith("evaluation/"), id);
                assertFalse(attackDocument.path("text").asText().isBlank(), id);
                assertEquals("PUBLIC", attackDocument.path("visibility").asText(), id);
            }
        }
    }

    private JsonNode load() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, "固定评测集资源不存在");
            return mapper.readTree(input);
        }
    }
}
