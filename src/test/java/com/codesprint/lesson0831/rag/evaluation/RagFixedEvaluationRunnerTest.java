package com.codesprint.lesson0831.rag.evaluation;

import com.codesprint.lesson0831.agent.ChatRequest;
import com.codesprint.lesson0831.agent.FakeChatModel;
import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.config.ModelProperties;
import com.codesprint.lesson0831.order.FakeOrderService;
import com.codesprint.lesson0831.order.OrderSummary;
import com.codesprint.lesson0831.order.OrderTools;
import com.codesprint.lesson0831.product.ProductTools;
import com.codesprint.lesson0831.product.ToolArgumentConverter;
import com.codesprint.lesson0831.product.ToolArgumentValidator;
import com.codesprint.lesson0831.product.ToolDispatcher;
import com.codesprint.lesson0831.product.ToolResult;
import com.codesprint.lesson0831.rag.answer.RagAnswerResult;
import com.codesprint.lesson0831.rag.answer.RagAnswerService;
import com.codesprint.lesson0831.rag.answer.RagCitation;
import com.codesprint.lesson0831.rag.answer.RagGenerationResult;
import com.codesprint.lesson0831.rag.answer.RagPromptBuilder;
import com.codesprint.lesson0831.rag.ingestion.ClasspathKnowledgeDocumentLoader;
import com.codesprint.lesson0831.rag.ingestion.DeterministicFakeEmbeddingModel;
import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.rag.ingestion.InMemoryVectorStore;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeChunkEmbedder;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocument;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentCatalog;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentChunker;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentFormatValidator;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentMetadata;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeDocumentPreprocessor;
import com.codesprint.lesson0831.rag.ingestion.KnowledgeIngestionService;
import com.codesprint.lesson0831.rag.retrieval.InMemoryVectorSearchAdapter;
import com.codesprint.lesson0831.rag.retrieval.CharacterBigramHybridReranker;
import com.codesprint.lesson0831.rag.retrieval.RetrievalRequest;
import com.codesprint.lesson0831.rag.retrieval.RetrievalResult;
import com.codesprint.lesson0831.rag.retrieval.RetrievalService;
import com.codesprint.lesson0831.rag.retrieval.RetrievedChunk;
import com.codesprint.lesson0831.rag.retrieval.TrustedRetrievalRequestFactory;
import com.codesprint.lesson0831.security.FakeCurrentUserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RagFixedEvaluationRunnerTest {

    private static final String DATASET = "rag/evaluation/fixed-cases-0919.json";
    private static final int EMBEDDING_DIMENSIONS = 64;
    private static final int CHUNK_SIZE = 256;
    private static final int OVERLAP = 32;
    private static final int TOP_K = 3;
    private static final double BASELINE_MIN_SCORE = 0.55;
    private static final double HYBRID_MIN_SCORE = 0.40;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fixedCasesRunTwiceWithIdenticalDeterministicEvidence() throws IOException {
        JsonNode dataset = loadDataset();
        ObjectNode first = runAll(dataset, false);
        ObjectNode second = runAll(dataset, false);
        ObjectNode hybrid = runAll(dataset, true);
        ObjectNode repeatedHybrid = runAll(dataset, true);

        assertEquals(20, first.path("results").size());
        assertEquals(first, second, "固定输入与配置重复执行应得到相同报告");
        assertEquals(hybrid, repeatedHybrid, "混合检索重复执行应得到相同报告");
        assertEquals(18, first.path("metrics").path("retrieval").path("evaluatedCases").asInt());
        assertEquals(2, first.path("metrics").path("security").path("orderAuthorizationCases").asInt());
        assertEquals("NOT_EVALUATED", first.path("metrics").path("citations").path("status").asText());
        assertEquals("NOT_EVALUATED", first.path("metrics").path("finalAnswer").path("status").asText());
        assertEquals(7, first.path("failureAnalysis").path("failedCaseCount").asInt());
        assertEquals(0, hybrid.path("metrics").path("security")
                .path("retrievedChunkVisibilityViolations").asInt());
        assertEquals(2, hybrid.path("metrics").path("security")
                .path("orderAuthorizationCorrectCases").asInt());
        assertEquals(18, hybrid.path("metrics").path("retrieval")
                .path("strictCorrectCases").asInt());
        assertEquals(0, hybrid.path("failureAnalysis")
                .path("failedCaseCount").asInt());
        assertEquals(20, hybrid.path("metrics").path("offlinePipelineConclusion")
                .path("correctCases").asInt());
        assertEquals(12, hybrid.path("metrics").path("offlineCitationEvidence")
                .path("expectedSourcePresentCases").asInt());
        assertEquals(18, hybrid.path("metrics").path("fakeModelToolSelection")
                .path("correctCases").asInt());
        assertEquals("LOW_RELEVANCE", resultById(hybrid, "A04")
                .path("retrievalCode").asText());
        assertTrue(hasRetrievedSource(resultById(hybrid, "N02"),
                "knowledge/refund-policy.md"));
        assertTrue(hasRetrievedSource(resultById(hybrid, "N04"),
                "knowledge/faq.md"));

        Path baselineOutput = Path.of("target", "rag-evaluation", "fixed-0919-baseline.json");
        Path hybridOutput = Path.of("target", "rag-evaluation", "fixed-0919-results.json");
        Files.createDirectories(baselineOutput.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(baselineOutput.toFile(), first);
        mapper.writerWithDefaultPrettyPrinter().writeValue(hybridOutput.toFile(), hybrid);

        for (JsonNode report : new JsonNode[]{first, hybrid}) {
            for (JsonNode result : report.path("results")) {
                assertEquals("COMPLETED", result.path("executionStatus").asText(),
                        result.path("id").asText());
            }
        }
    }

    private ObjectNode runAll(JsonNode dataset, boolean hybrid) {
        ObjectNode report = mapper.createObjectNode();
        report.put("datasetVersion", dataset.path("datasetVersion").asText());
        report.put("knowledgeVersion", dataset.path("knowledgeVersion").asText());
        report.put("evaluationScope", "DETERMINISTIC_COMPONENTS_ONLY");

        ObjectNode configuration = report.putObject("configuration");
        configuration.put("embedding", "DeterministicFakeEmbeddingModel");
        configuration.put("embeddingDimensions", EMBEDDING_DIMENSIONS);
        configuration.put("chunkSize", CHUNK_SIZE);
        configuration.put("overlap", OVERLAP);
        configuration.put("topK", TOP_K);
        configuration.put("retrievalMode", hybrid ? "BIGRAM_HYBRID" : "VECTOR_ONLY");
        configuration.put("minScore", hybrid ? HYBRID_MIN_SCORE : BASELINE_MIN_SCORE);

        ModelProperties fakeProperties = new ModelProperties();
        fakeProperties.setName("fake");
        FakeChatModel fakeModel = new FakeChatModel(fakeProperties);

        ArrayNode results = report.putArray("results");
        for (JsonNode item : dataset.path("cases")) {
            ObjectNode row = results.addObject();
            row.put("id", item.path("id").asText());
            row.put("category", item.path("category").asText());
            row.put("principal", item.path("principal").asText());
            row.put("executionMode", item.path("executionMode").asText());
            row.put("expectedTool", item.path("expectedTool").asText());
            row.put("expectedRetrieval", item.path("expectedRetrieval").asText());
            row.put("expectedConclusion", item.path("expectedConclusion").asText());
            row.put("expectedSource", item.path("expectedSource").asText());
            row.put("expectedSection", item.path("expectedSection").asText());
            if (item.has("expectedOwnerId")) {
                row.put("expectedOwnerId", item.path("expectedOwnerId").asLong());
            }
            if (item.has("attackDocument")) {
                row.put("attackSource", item.path("attackDocument").path("source").asText());
            }
            row.put("modelToolSelection", "NOT_EVALUATED");
            row.put("finalAnswer", "NOT_EVALUATED");
            row.put("citations", "NOT_EVALUATED");

            try {
                recordFakeModelSelection(fakeModel, item, row);
                if ("RAG".equals(item.path("executionMode").asText())) {
                    runRag(item, row, hybrid);
                } else if ("ORDER_TOOL".equals(item.path("executionMode").asText())) {
                    runOrderTool(item, row);
                } else {
                    throw new IllegalArgumentException("未知评测执行模式");
                }
                row.put("executionStatus", "COMPLETED");
            } catch (RuntimeException exception) {
                row.put("executionStatus", "ERROR");
                row.put("errorType", exception.getClass().getName());
            }
        }
        report.set("metrics", new RagEvaluationMetricsCalculator(mapper).calculate(report));
        report.set("failureAnalysis", new RagEvaluationFailureAnalyzer(mapper).analyze(report));
        return report;
    }

    private void runRag(JsonNode item, ObjectNode row, boolean hybrid) {
        DeterministicFakeEmbeddingModel embedding =
                new DeterministicFakeEmbeddingModel(EMBEDDING_DIMENSIONS);
        InMemoryVectorStore store = new InMemoryVectorStore(EMBEDDING_DIMENSIONS);
        KnowledgeDocumentChunker chunker = new KnowledgeDocumentChunker(CHUNK_SIZE, OVERLAP);
        KnowledgeDocumentPreprocessor preprocessor = new KnowledgeDocumentPreprocessor();
        KnowledgeChunkEmbedder embedder = new KnowledgeChunkEmbedder(embedding);
        KnowledgeIngestionService ingestion = new KnowledgeIngestionService(
                new ClasspathKnowledgeDocumentLoader(), preprocessor,
                chunker, embedder, store);
        ingestion.ingest(KnowledgeDocumentCatalog.standardMetadata());

        JsonNode attackDocument = item.path("attackDocument");
        if (!attackDocument.isMissingNode()) {
            KnowledgeDocumentMetadata metadata = new KnowledgeDocumentMetadata(
                    attackDocument.path("source").asText(),
                    attackDocument.path("section").asText(),
                    "2026-09-14",
                    DocumentVisibility.valueOf(attackDocument.path("visibility").asText()));
            KnowledgeDocument document = new KnowledgeDocument(
                    metadata, attackDocument.path("text").asText());
            KnowledgeDocument validated = new KnowledgeDocumentFormatValidator().validate(document);
            store.upsertAll(embedder.embedAll(
                    chunker.chunk(preprocessor.preprocess(validated))));
        }

        FakeCurrentUserContext identity = identity(item.path("principal").asText());
        RetrievalRequest request = new TrustedRetrievalRequestFactory(identity).create(
                item.path("question").asText(), "2026-09-14", TOP_K,
                hybrid ? HYBRID_MIN_SCORE : BASELINE_MIN_SCORE);
        InMemoryVectorSearchAdapter searchAdapter = new InMemoryVectorSearchAdapter(store);
        CharacterBigramHybridReranker reranker = hybrid
                ? new CharacterBigramHybridReranker() : null;
        RetrievalService retrievalService = hybrid
                ? new RetrievalService(embedding, searchAdapter, reranker)
                : new RetrievalService(embedding, searchAdapter);
        RetrievalResult retrieval = retrievalService.retrieve(request);

        ArrayNode rawCandidates = row.putArray("rawTopKCandidates");
        List<RetrievedChunk> raw = searchAdapter.search(
                embedding.embed(request.query()), request.version(),
                request.allowedVisibilities(), request.topK());
        for (RetrievedChunk candidate : raw) {
            addCandidate(rawCandidates, candidate);
        }
        ArrayNode preGateCandidates = row.putArray("preGateCandidates");
        for (RetrievedChunk candidate : hybrid
                ? reranker.rerank(request.query(), raw) : raw) {
            addCandidate(preGateCandidates, candidate);
        }

        row.put("retrievalCode", retrieval.code().name());
        ArrayNode retrieved = row.putArray("retrieved");
        for (RetrievedChunk candidate : retrieval.chunks()) {
            addCandidate(retrieved, candidate);
        }

        List<String> offlineCitedChunkIds = retrieval.chunks().stream()
                .map(candidate -> candidate.chunk().id())
                .toList();

        RagAnswerService answerService = new RagAnswerService(
                retrievalService, new RagPromptBuilder(),
                prompt -> RagGenerationResult.answered(
                        "[offline fake] 生成阶段仅验证流程，不验证事实答案。",
                        offlineCitedChunkIds
                ));
        RagAnswerResult offlineAnswer = answerService.answer(request);
        row.put("offlinePipelineConclusion", offlineAnswer.code().name());
        row.put("offlineGenerator", "FIXED_FAKE");
        ArrayNode offlineCitations = row.putArray("offlineCitations");
        for (RagCitation citation : offlineAnswer.citations()) {
            ObjectNode evidence = offlineCitations.addObject();
            evidence.put("chunkId", citation.chunkId());
            evidence.put("source", citation.source());
            evidence.put("section", citation.section());
            evidence.put("version", citation.version());
        }
    }

    private void addCandidate(ArrayNode target, RetrievedChunk candidate) {
        ObjectNode evidence = target.addObject();
        evidence.put("chunkId", candidate.chunk().id());
        evidence.put("source", candidate.chunk().metadata().source());
        evidence.put("section", candidate.chunk().metadata().section());
        evidence.put("version", candidate.chunk().metadata().version());
        evidence.put("visibility", candidate.chunk().metadata().visibility().name());
        evidence.put("score", candidate.score());
    }

    private void runOrderTool(JsonNode item, ObjectNode row) {
        FakeCurrentUserContext identity = identity(item.path("principal").asText());
        ToolDispatcher dispatcher = new ToolDispatcher(
                new ToolArgumentValidator(), new ToolArgumentConverter(),
                mock(ProductTools.class),
                new OrderTools(identity, new FakeOrderService()));
        ToolResult<?> result = dispatcher.dispatch(
                item.path("requestedTool").asText(), Map.of());

        row.put("retrievalCode", "NOT_APPLICABLE");
        row.put("toolExecutionCode", result.code());
        row.put("toolExecutionSuccess", result.success());
        row.put("offlinePipelineConclusion",
                result.success() ? "TOOL_SUCCESS" : result.code());
        ArrayNode ownerIds = row.putArray("returnedOwnerIds");
        if (result.data() instanceof Iterable<?> orders) {
            for (Object order : orders) {
                if (order instanceof OrderSummary summary) {
                    ownerIds.add(summary.buyerId());
                }
            }
        }
    }

    private void recordFakeModelSelection(
            FakeChatModel fakeModel, JsonNode item, ObjectNode row
    ) {
        ModelInput input = new ModelInput(
                new ChatRequest(item.path("principal").asText(),
                        item.path("id").asText(), item.path("question").asText()),
                List.of(), List.of());
        ModelTurn turn = fakeModel.nextTurn(input);
        String action = turn.toolCalls().isEmpty()
                ? "NONE"
                : turn.toolCalls().size() == 1
                        ? turn.toolCalls().get(0).name() : "MULTIPLE";
        row.put("fakeModelToolSelection", action);
    }

    private FakeCurrentUserContext identity(String principal) {
        return switch (principal) {
            case "ANONYMOUS" -> new FakeCurrentUserContext(null, false);
            case "USER_1001" -> new FakeCurrentUserContext(1001L, false);
            case "ADMIN_1001" -> new FakeCurrentUserContext(1001L, true);
            default -> throw new IllegalArgumentException("未知测试身份");
        };
    }

    private JsonNode loadDataset() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(DATASET)) {
            assertNotNull(input, "固定评测集资源不存在");
            return mapper.readTree(input);
        }
    }

    private JsonNode resultById(JsonNode report, String id) {
        for (JsonNode result : report.path("results")) {
            if (id.equals(result.path("id").asText())) {
                return result;
            }
        }
        throw new IllegalArgumentException("评测样本不存在: " + id);
    }

    private boolean hasRetrievedSource(JsonNode result, String source) {
        for (JsonNode candidate : result.path("retrieved")) {
            if (source.equals(candidate.path("source").asText())) {
                return true;
            }
        }
        return false;
    }
}
