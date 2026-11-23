package com.codesprint.lesson0831.demo;

import com.codesprint.lesson0831.rag.answer.RagAnswerResult;
import com.codesprint.lesson0831.rag.answer.RagAnswerService;
import com.codesprint.lesson0831.rag.retrieval.TrustedRetrievalRequestFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/demo")
@Profile("dev & demo & !prod")
public class DemoController {
    private final DemoKnowledgeRuntime answers;
    private final TrustedRetrievalRequestFactory requests;

    public DemoController(DemoKnowledgeRuntime answers, TrustedRetrievalRequestFactory requests) {
        this.answers = answers;
        this.requests = requests;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of("name", "ShopGuard", "mode", "DETERMINISTIC_DEMO", "externalModel", false,
                "identity", "固定演示用户 1001", "knowledgeVersion", "2026-09-14");
    }

    @PostMapping("/knowledge")
    public RagAnswerResult knowledge(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody KnowledgeRequest request) {
        String previous = MDC.get("requestId");
        MDC.put("requestId", requestId != null && requestId.matches("[A-Za-z0-9._-]{1,64}")
                ? requestId : UUID.randomUUID().toString());
        try {
            // 版本、可见性、Top-K 和阈值均由服务器决定，客户端只传问题。
            return answers.answer(requests.create(request.question(), "2026-09-14", 3, 0.40));
        } finally {
            if (previous == null) MDC.remove("requestId");
            else MDC.put("requestId", previous);
        }
    }

    public record KnowledgeRequest(
            @NotBlank(message = "问题不能为空")
            @Size(max = 1000, message = "问题不能超过1000个字符") String question) {
    }
}
