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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Spring 应用链路测试；MockMvc，无真实网络，真实工具/RAG + 确定性替身。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "demo"})
class DemoApplicationTest {
    @Autowired MockMvc mvc;
    @Autowired ConversationMemory memory;

    @Test
    void infoDisclosesFakeBoundary() throws Exception {
        mvc.perform(get("/api/demo/info")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DETERMINISTIC_DEMO"))
                .andExpect(jsonPath("$.externalModel").value(false));
    }

    @Test
    void productSearchUsesDispatcherAndReturnsRealFixture() throws Exception {
        ask("search", "搜索 Java").andExpect(jsonPath("$.answer").value(containsString("Java 入门书")));
    }

    @Test
    void detailAndMissingProductHaveStableResults() throws Exception {
        ask("detail", "商品 2 详情").andExpect(jsonPath("$.answer").value(containsString("89.90")));
        ask("missing", "商品 999 详情").andExpect(jsonPath("$.answer").value(containsString("NOT_FOUND")));
    }

    @Test
    void oversizedProductIdIsRejectedWithoutOverflow() throws Exception {
        ask("overflow", "商品 9223372036854775808 详情")
                .andExpect(jsonPath("$.answer").value(containsString("INVALID_ARGUMENT")));
    }

    @Test
    void ordersBelongToTrustedUser() throws Exception {
        ask("orders", "我的订单").andExpect(jsonPath("$.answer").value(containsString("Java编程思想")))
                .andExpect(jsonPath("$.answer").value(not(containsString("数据结构"))));
    }

    @Test
    void forgedBuyerIdIsRejected() throws Exception {
        ask("attack", "查看他人 1002 的订单")
                .andExpect(jsonPath("$.answer").value(containsString("UNKNOWN_ARGUMENT")))
                .andExpect(jsonPath("$.answer").value(not(containsString("数据结构"))));
    }

    @Test
    void successfulConversationIsStoredUnderServerIdentity() throws Exception {
        mvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON).content("""
                {"sessionId":"identity-demo","question":"我的订单","userId":"1002"}
                """)).andExpect(status().isOk());
        assertThat(memory.load(new ConversationKey("1001", "identity-demo"))).hasSize(2);
        assertThat(memory.load(new ConversationKey("1002", "identity-demo"))).isEmpty();
    }

    @Test
    void ragReturnsRealRetrievedCitationMetadata() throws Exception {
        mvc.perform(post("/api/demo/knowledge").contentType(MediaType.APPLICATION_JSON).content("""
                {"question":"已经发货的订单能直接取消吗？应该先做什么？"}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("ANSWERED"))
                .andExpect(jsonPath("$.answer").value(containsString("已发货订单不能直接取消")))
                .andExpect(jsonPath("$.citations[0].source").value("knowledge/refund-policy.md"))
                .andExpect(jsonPath("$.citations[0].version").value("2026-09-14"));
    }

    @Test
    void insufficientEvidenceHasNoCitation() throws Exception {
        mvc.perform(post("/api/demo/knowledge").contentType(MediaType.APPLICATION_JSON).content("""
                {"question":"周末人工客服的上班时间是几点？"}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.citations").isEmpty());
    }

    @Test
    void callerCannotSupplyVisibilityOrAdministratorIdentity() throws Exception {
        mvc.perform(post("/api/demo/knowledge").contentType(MediaType.APPLICATION_JSON).content("""
                {"question":"退款","admin":true,"allowedVisibilities":["ADMIN_ONLY"]}
                """)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求格式不合法，仅接受 question 字段"));
    }

    @Test
    void emptyKnowledgeQuestionIsRejected() throws Exception {
        mvc.perform(post("/api/demo/knowledge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\" \"}")).andExpect(status().isBadRequest());
    }

    @Test
    void oversizedKnowledgeQuestionIsRejected() throws Exception {
        mvc.perform(post("/api/demo/knowledge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"" + "问".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions ask(String session, String question) throws Exception {
        return mvc.perform(post("/api/assistant/chat").header("X-Request-Id", "demo-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + session + "\",\"question\":\"" + question + "\"}"))
                .andExpect(status().isOk());
    }
}
