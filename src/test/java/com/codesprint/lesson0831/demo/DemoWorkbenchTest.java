package com.codesprint.lesson0831.demo;

import com.codesprint.lesson0831.agent.ConversationKey;
import com.codesprint.lesson0831.agent.ConversationMemory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 Spring 组件 + MockMvc；无真实网络/数据库/外部模型。 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles({"dev", "demo"})
class DemoWorkbenchTest {
    @Autowired MockMvc mvc;
    @Autowired ConversationMemory memory;
    @Test void tracedChatSavesUnderTrustedIdentity() throws Exception {
        mvc.perform(post("/api/demo/workbench/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"workbench-trace\",\"question\":\"我的订单\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.savedMessages").value(2))
                .andExpect(jsonPath("$.trace[0].stage").value("IDENTITY"))
                .andExpect(jsonPath("$.answer").value(containsString("Java编程思想")));
        assertThat(memory.load(new ConversationKey("1001", "workbench-trace"))).hasSize(2);
        mvc.perform(get("/api/demo/workbench/session").param("sessionId", "workbench-trace"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].role").value("user"));
    }
    @Test void callerCannotInjectIdentityIntoWorkbench() throws Exception {
        mvc.perform(post("/api/demo/workbench/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"rejected\",\"question\":\"我的订单\",\"userId\":1002}"))
                .andExpect(status().isBadRequest());
        assertThat(memory.load(new ConversationKey("1001", "rejected"))).isEmpty();
    }
    @Test void validationRejectsEmptyAndOversizedQuestions() throws Exception {
        for(String question : new String[]{"", "问".repeat(1001)})
            mvc.perform(post("/api/demo/workbench/chat").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sessionId\":\"invalid-workbench\",\"question\":\""+question+"\"}"))
                    .andExpect(status().isBadRequest());
    }
    @Test void stableReimportIsIdempotentAndKeepsRetrievalAvailable() throws Exception {
        mvc.perform(post("/api/demo/workbench/ingestion")).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.unchanged").value(4));
        mvc.perform(get("/api/demo/workbench/knowledge")).andExpect(status().isOk())
                .andExpect(jsonPath("$.storedChunks").value(4)).andExpect(jsonPath("$.dimensions").value(64));
        mvc.perform(post("/api/demo/knowledge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"已经发货的订单能直接取消吗？应该先做什么？\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("ANSWERED"));
    }
    @Test void originalEvaluationMatrixExposesRuleModelLimitations() throws Exception {
        mvc.perform(post("/api/demo/workbench/evaluation")).andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.total").value(12))
                .andExpect(jsonPath("$.metrics.accuracy").value(org.hamcrest.Matchers.lessThan(1.0)));
    }
    @Test void allFaultsReturnSafeResultsAndNormalQueriesStillWork() throws Exception {
        String[] kinds={"MODEL","TOOL","RETRIEVAL","GENERATION","CITATION","LOOP"};
        String[] codes={"MODEL_UNAVAILABLE","INTERNAL_ERROR","RETRIEVAL_UNAVAILABLE","GENERATION_UNAVAILABLE","GENERATION_UNAVAILABLE","ROUND_LIMIT"};
        for(int i=0;i<kinds.length;i++)mvc.perform(post("/api/demo/workbench/fault").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\""+kinds[i]+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(codes[i]))
                .andExpect(jsonPath("$.savedMessages").value(0)).andExpect(jsonPath("$.citations").isEmpty())
                .andExpect(jsonPath("$.isolated").value(true));
        mvc.perform(post("/api/demo/workbench/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"after-fault\",\"question\":\"搜索 Java\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value(containsString("Java 入门书")));
    }
    @Test void unknownFaultKindCannotActivateUnboundedBehavior() throws Exception {
        mvc.perform(post("/api/demo/workbench/fault").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"ALL_USERS\"}")) .andExpect(status().isBadRequest());
    }
    @Test void sseHasCompletionMarkerAndUsesActualResult() throws Exception {
      // 连续触发真实异步写入与安全过滤器，回归首次 flush 的响应头竞态。
      for(int attempt=0;attempt<20;attempt++) {
        var result=mvc.perform(post("/api/demo/workbench/chat/stream").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"stream-test-"+attempt+"\",\"question\":\"商品 2 详情\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        result.getAsyncResult(5000);
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("COMPLETION_REPLAY")))
                .andExpect(content().string(containsString("event: delta")))
                .andExpect(content().string(containsString("event: done")))
                .andExpect(content().string(containsString("89.90")));
        assertThat(memory.load(new ConversationKey("1001", "stream-test-"+attempt))).hasSize(2);
      }
    }
    @Test void observabilityReturnsActualTimersAndNoProductionClaim() throws Exception {
        mvc.perform(get("/api/demo/workbench/observability")).andExpect(status().isOk())
                .andExpect(jsonPath("$.externalModel").value(false)).andExpect(jsonPath("$.productionMonitoring").value(false));
    }
}
