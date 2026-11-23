package com.codesprint.lesson0831.demo;

import com.codesprint.lesson0831.agent.*;
import com.codesprint.lesson0831.agent.evaluation.*;
import com.codesprint.lesson0831.agent.toolcalling.*;
import com.codesprint.lesson0831.order.OrderTools;
import com.codesprint.lesson0831.product.*;
import com.codesprint.lesson0831.rag.answer.*;
import com.codesprint.lesson0831.rag.ingestion.IngestionReport;
import com.codesprint.lesson0831.rag.retrieval.TrustedRetrievalRequestFactory;
import com.codesprint.lesson0831.security.CurrentUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Duration;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/demo/workbench")
@Profile("dev & demo & !prod")
public class DemoWorkbenchController {
    private final AssistantService assistant;
    private final ConversationMemory memory;
    private final CurrentUserContext identity;
    private final DemoKnowledgeRuntime knowledge;
    private final TrustedRetrievalRequestFactory retrievalRequests;
    private final ChatModel model;
    private final ToolCallExecutor executor;
    private final OrderTools orders;
    private final MeterRegistry meters;
    private final ObjectMapper json;

    public DemoWorkbenchController(AssistantService assistant, ConversationMemory memory,
            CurrentUserContext identity, DemoKnowledgeRuntime knowledge, TrustedRetrievalRequestFactory retrievalRequests,
            ChatModel model, ToolCallExecutor executor, OrderTools orders, MeterRegistry meters, ObjectMapper json) {
        this.assistant = assistant; this.memory = memory; this.identity = identity; this.knowledge = knowledge;
        this.retrievalRequests = retrievalRequests; this.model = model; this.executor = executor;
        this.orders = orders; this.meters = meters; this.json = json;
    }

    @PostMapping("/chat")
    public synchronized WorkbenchAnswer chat(@Valid @RequestBody WorkbenchRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String safeId = requestId != null && requestId.matches("[A-Za-z0-9._-]{1,64}") ? requestId : UUID.randomUUID().toString();
        String previous = MDC.get("requestId");
        MDC.put("requestId", safeId);
        long start = System.nanoTime();
        DemoTrace.begin();
        try {
            DemoTrace.add("IDENTITY", "TRUSTED", "服务器用户 " + identity.getUserId());
            DemoTrace.add("MEMORY", "LOAD", "当前会话历史 " + session(request.sessionId()).size() + " 条");
            var response = assistant.ask(new ChatRequest(null, request.sessionId(), request.question()));
            int saved = session(request.sessionId()).size();
            DemoTrace.add("MEMORY", "APPEND", "成功后保存 user / assistant，当前 " + saved + " 条");
            return new WorkbenchAnswer(response.answer(), safeId, (System.nanoTime() - start) / 1_000_000,
                    saved, DemoTrace.snapshot());
        } finally {
            DemoTrace.clear();
            if (previous == null) MDC.remove("requestId"); else MDC.put("requestId", previous);
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody stream(@Valid @RequestBody WorkbenchRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletResponse response) throws IOException {
        // 先完成业务与保存。SSE 取消只停止传输，不承诺撤销已保存轮次。
        WorkbenchAnswer answer = chat(request, requestId);
        // 请求线程先提交完整安全头，避免异步首次 flush 与延迟 HeaderWriter 同时写头。
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Accel-Buffering", "no");
        response.flushBuffer();
        return output -> DemoSseWriter.write(output, json, answer);
    }

    @GetMapping("/session")
    public synchronized List<ChatMessage> session(@RequestParam @Size(min = 1, max = 100) String sessionId) {
        if (sessionId.isBlank() || sessionId.length() > 100) throw new IllegalArgumentException("会话标识不合法");
        return List.copyOf(memory.load(new ConversationKey(String.valueOf(identity.getUserId()), sessionId)));
    }

    @GetMapping("/knowledge")
    public Map<String, Object> knowledge() { return knowledge.overview(); }

    @PostMapping("/ingestion")
    public IngestionReport ingestion() { return knowledge.reimport(); }

    @PostMapping("/evaluation")
    public ToolSelectionEvaluationReport evaluation() {
        return new ToolSelectionEvaluationRunner(model, new ToolSelectionEvaluation(), new ToolSelectionReportCalculator())
                .run(ToolSelectionMatrix.standardCases());
    }

    @GetMapping("/observability")
    public Map<String, Object> observability() {
        var timers = meters.find("agent.operation.duration").timers().stream().map(timer -> Map.of(
                "stage", Objects.toString(timer.getId().getTag("stage"), "UNAVAILABLE"),
                "resultCode", Objects.toString(timer.getId().getTag("resultCode"), "UNAVAILABLE"),
                "count", timer.count(), "totalMs", timer.totalTime(TimeUnit.MILLISECONDS),
                "maxMs", timer.max(TimeUnit.MILLISECONDS))).toList();
        return Map.of("timers", timers, "externalModel", false, "productionMonitoring", false);
    }

    @PostMapping("/fault")
    public Map<String, Object> fault(@Valid @RequestBody FaultRequest request) {
        String question = "已经发货的订单能直接取消吗？应该先做什么？";
        var retrieval = retrievalRequests.create(question, "2026-09-14", 3, 0.40);
        if (request.kind() == FaultKind.RETRIEVAL) return faultResult(request.kind(), knowledge.retrievalFailure(retrieval));
        if (request.kind() == FaultKind.GENERATION) return faultResult(request.kind(), knowledge.answerWith(
                prompt -> { throw new IllegalStateException("demo generation failure"); }, retrieval));
        if (request.kind() == FaultKind.CITATION) return faultResult(request.kind(), knowledge.answerWith(
                prompt -> RagGenerationResult.answered("这个文本不能进入回答", List.of("not-retrieved")), retrieval));
        if (request.kind() == FaultKind.TOOL) {
            var failing = new ProductService() {
                public List<ProductSummary> search(ProductSearchRequest query) { throw new IllegalStateException("demo failure"); }
                public ProductDetail getDetail(long id) { throw new IllegalStateException("demo failure"); }
            };
            var dispatcher = new ToolDispatcher(new ToolArgumentValidator(), new ToolArgumentConverter(),
                    new ProductTools(failing), orders);
            var result = dispatcher.dispatch("get_product_detail", Map.of("productId", 1L));
            return Map.of("kind", request.kind(), "code", result.code(), "message", result.message(),
                    "citations", List.of(), "isolated", true, "savedMessages", 0);
        }
        var isolatedMemory = new InMemoryConversationMemory();
        ChatModel failingModel = new ChatModel() {
            public String chat(ChatRequest input, List<ChatMessage> history) { throw new ModelCallException("demo model failure"); }
            public ModelTurn nextTurn(ModelInput input) {
                if (request.kind() == FaultKind.MODEL) throw new ModelCallException("demo model failure");
                return new ModelTurn(null, List.of(new ToolCall("loop", "list_my_orders", Map.of())));
            }
        };
        var service = new AssistantService(new ToolCallingRunner(failingModel, executor, 1, Duration.ofSeconds(2)), isolatedMemory, identity);
        try {
            service.ask(new ChatRequest(null, "isolated-fault", "我的订单"));
            throw new IllegalStateException("故障样例未触发预期边界");
        } catch (ModelCallException failure) {
            return Map.of("kind", request.kind(), "code", request.kind() == FaultKind.MODEL ? "MODEL_UNAVAILABLE" : "ROUND_LIMIT",
                    "message", "请求安全结束，未保存失败轮次。", "citations", List.of(), "isolated", true,
                    "savedMessages", isolatedMemory.load(new ConversationKey(String.valueOf(identity.getUserId()), "isolated-fault")).size());
        }
    }

    private Map<String, Object> faultResult(FaultKind kind, RagAnswerResult result) {
        return Map.of("kind", kind, "code", result.code(), "message", result.answer(), "citations", result.citations(),
                "isolated", true, "savedMessages", 0);
    }
    public enum FaultKind { MODEL, TOOL, RETRIEVAL, GENERATION, CITATION, LOOP }
    public record FaultRequest(@NotNull FaultKind kind) {}
    public record WorkbenchRequest(@NotBlank @Size(max = 100) String sessionId, @NotBlank @Size(max = 1000) String question) {}
    public record WorkbenchAnswer(String answer, String requestId, long elapsedMs, int savedMessages, List<DemoTrace.Event> trace) {}
}
