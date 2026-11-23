package com.codesprint.lesson0831.platform;

import com.codesprint.lesson0831.agent.*;
import com.codesprint.lesson0831.agent.toolcalling.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"SHOPGUARD_DB_URL=jdbc:h2:mem:platform-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "SHOPGUARD_ADMIN_PASSWORD=VerificationAdmin!2026","SHOPGUARD_STUDENT_PASSWORD=VerificationStudent!2026",
    "GATEWAY_API_KEY=platform-test-key","GATEWAY_MODEL=campus-chat","GATEWAY_TIMEOUT=60s","MODEL_MODE=stub"})
@ActiveProfiles({"prod","platform","gateway"})
@AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformApplicationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
    @MockBean ChatModel model;@MockBean PlatformGatewayClient gateway;@MockBean PlatformVectorIndex vectors;
    record Browser(MockHttpSession session,String csrf){}
    @BeforeEach void resetData(){
        for(String table:List.of("platform_runs","platform_sessions","conversation_messages","platform_chunks","platform_documents","trade_payment_requests","trade_order_details","orders"))jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE trade_inventory SET available_quantity=5,reserved_quantity=0,sold_quantity=0");
        when(model.nextTurn(any(ModelInput.class),any(TimeBudget.class))).thenReturn(new ModelTurn("回答来自业务模型",List.of()));
        when(gateway.embed(anyList())).thenAnswer(call->{List<String> input=call.getArgument(0);return input.stream().map(s->List.of(0.2,0.3,0.8)).toList();});
        when(vectors.search(anyList(),anyInt(),anyDouble())).thenAnswer(call->jdbc.query("SELECT id FROM platform_chunks ORDER BY chunk_index LIMIT 4",(rs,row)->new PlatformVectorIndex.Hit(rs.getString(1),0.9)));
    }
    Browser anonymous()throws Exception{
        var response=mvc.perform(get("/api/platform/session")).andExpect(status().isOk()).andReturn();
        return new Browser((MockHttpSession)response.getRequest().getSession(false),json.readTree(response.getResponse().getContentAsString()).path("csrf").asText());
    }
    Browser login(String name)throws Exception{
        var browser=anonymous();String pass=name.equals("admin")?"VerificationAdmin!2026":"VerificationStudent!2026";
        mvc.perform(post("/api/platform/login").session(browser.session()).header("X-CSRF-TOKEN",browser.csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("username",name,"password",pass)))).andExpect(status().isOk());return browser;
    }
    String session(Browser browser,String mode)throws Exception{
        var response=mvc.perform(post("/api/platform/sessions").session(browser.session()).header("X-CSRF-TOKEN",browser.csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("mode",mode))))
            .andExpect(status().isOk()).andReturn();return json.readTree(response.getResponse().getContentAsString()).path("id").asText();
    }
    ResultActions ask(Browser browser,String session,String question)throws Exception{
        return mvc.perform(post("/api/platform/chat").session(browser.session()).header("X-CSRF-TOKEN",browser.csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("sessionId",session,"question",question))));
    }
    @Test void anonymousCannotReadSessionsOrTrade()throws Exception{
        mvc.perform(get("/api/platform/sessions")).andExpect(status().isUnauthorized());mvc.perform(get("/api/trade/orders")).andExpect(status().isUnauthorized());
    }
    @Test void unrelatedQuestionsNeverReachTheModelOrKnowledgeIndex()throws Exception{
        var user=login("student");
        for(String mode:List.of("business","knowledge")){
            String id=session(user,mode);
            for(String question:List.of("法国的首都是哪里","帮我写一首诗","明天北京天气怎么样","写一个 Java 快速排序程序")){
                ask(user,id,question).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OUT_OF_SCOPE"));
            }
        }
        verifyNoInteractions(model,gateway,vectors);
    }
    @Test void unsupportedFreeTextFromModelCannotBecomeACustomerServiceAnswer()throws Exception{
        var user=login("student");
        when(model.nextTurn(any(ModelInput.class),any(TimeBudget.class))).thenReturn(new ModelTurn("巴黎是法国的首都。你的退款已经到账。",List.of()));
        ask(user,session(user,"business"),"查询我的订单").andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("UNVERIFIED_ANSWER"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("巴黎"))));
    }
    @Test void streamedDraftAndUnsupportedFinalTextNeverReachBrowserOrMemory()throws Exception{
        var user=login("student");String id=session(user,"business");
        when(model.nextTurn(any(ModelInput.class),any(TimeBudget.class))).thenAnswer(call->{
            com.codesprint.lesson0831.observability.AgentTrace.event("delta","巴黎；退款已经到账");
            return new ModelTurn("巴黎；退款已经到账",List.of());
        });
        var pending=mvc.perform(post("/api/platform/chat/stream").session(user.session()).header("X-CSRF-TOKEN",user.csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("sessionId",id,"question","查询我的订单")))).andExpect(request().asyncStarted()).andReturn();
        var response=mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(response.contains("UNVERIFIED_ANSWER"));assertFalse(response.contains("event: delta"));assertFalse(response.contains("巴黎"));
        assertFalse(jdbc.queryForObject("SELECT content FROM conversation_messages WHERE role='assistant'",String.class).contains("退款已经到账"));
    }
    @Test void businessKeywordCannotMakeFreeTextOrUnapprovedToolsVisible()throws Exception{
        var user=login("student");String id=session(user,"business");
        when(model.nextTurn(any(ModelInput.class),any(TimeBudget.class))).thenReturn(new ModelTurn("模型自称已退款",List.of(
            new ToolCall("read","list_my_orders",Map.of()),new ToolCall("write","refund_order",Map.of("orderId",99101)))));
        ask(user,id,"查询订单").andExpect(status().isOk()).andExpect(jsonPath("$.code").value("UNVERIFIED_ANSWER"))
            .andExpect(jsonPath("$.trace.tools.length()").value(0));
        verify(model,times(1)).nextTurn(any(ModelInput.class),any(TimeBudget.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class));
    }
    @Test void validToolResultIsRenderedWhileModelProseIsDiscarded()throws Exception{
        var user=login("student");
        when(model.nextTurn(any(ModelInput.class),any(TimeBudget.class))).thenReturn(new ModelTurn("巴黎是首都；退款已经到账",List.of(new ToolCall("search","search_products",Map.of("keyword","Java","page",1,"size",10,"sort","price_asc")))));
        ask(user,session(user,"business"),"查询 Java 商品").andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("Java 核心技术")))
            .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("当前可售")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("巴黎"))));
        verify(model,times(1)).nextTurn(any(ModelInput.class),any(TimeBudget.class));
    }
    @Test void modelCannotReadAnotherBuyersOrderEvenWithAllowedToolName()throws Exception{
        var user=login("student");jdbc.update("INSERT INTO orders(order_id,buyer_id,product_name,status) VALUES (99101,1001,'PRIVATE_ORDER_TITLE','PAID')");
        when(model.nextTurn(any(ModelInput.class),any(TimeBudget.class))).thenReturn(new ModelTurn("",List.of(new ToolCall("foreign","get_my_order",Map.of("orderId","99101")))));
        ask(user,session(user,"business"),"查询订单 99101").andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("没有找到")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE_ORDER_TITLE"))));
    }
    @Test void followUpRefreshesOwnedRecordButNewQuestionDoesNotReusePreviousOrder()throws Exception{
        var user=login("student");String id=session(user,"business");
        jdbc.update("INSERT INTO orders(order_id,buyer_id,product_name,status) VALUES (99102,1002,'学生的订单','PAID')");
        jdbc.update("INSERT INTO orders(order_id,buyer_id,product_name,status) VALUES (99103,1002,'另一笔订单','PAID')");
        when(model.nextTurn(any(ModelInput.class),any(TimeBudget.class))).thenReturn(new ModelTurn("",List.of(new ToolCall("first","get_my_order",Map.of("orderId","99102")))));
        ask(user,id,"查询订单 99102").andExpect(jsonPath("$.support.orderId").value("99102"));
        jdbc.update("UPDATE orders SET status='CANCELLED' WHERE order_id=99102");
        when(model.nextTurn(any(ModelInput.class),any(TimeBudget.class))).thenAnswer(call->{
            ModelInput input=call.getArgument(0);assertTrue(input.request().question().contains("关联订单编号：99102"));
            return new ModelTurn("",List.of(new ToolCall("follow","get_my_order",Map.of("orderId","99102"))));
        });
        ask(user,id,"那现在怎么样").andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("已取消")));
        doAnswer(call->{ModelInput input=call.getArgument(0);assertFalse(input.request().question().contains("关联订单编号：99102"));
            return new ModelTurn("",List.of(new ToolCall("new","get_my_order",Map.of("orderId","99103"))));
        }).when(model).nextTurn(any(ModelInput.class),any(TimeBudget.class));
        ask(user,id,"查询订单 99103").andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("另一笔订单")));
    }
    @Test void rejectedTurnCannotCarryPriorContextIntoNextAmbiguousQuestion()throws Exception{
        var user=login("student");String id=session(user,"business");
        ask(user,id,"帮我写一首诗").andExpect(jsonPath("$.code").value("OUT_OF_SCOPE"));
        ask(user,id,"继续").andExpect(jsonPath("$.code").value("NEEDS_CLARIFICATION"));
        verifyNoInteractions(model,gateway,vectors);
    }
    @Test void explicitNewOrderInFollowUpWinsOverEarlierOrderContext()throws Exception{
        var user=login("student");String id=session(user,"business");
        jdbc.update("INSERT INTO orders(order_id,buyer_id,product_name,status) VALUES (99102,1002,'第一笔订单','PAID'),(99103,1002,'第二笔订单','PAID')");
        when(model.nextTurn(any(),any())).thenReturn(new ModelTurn("",List.of(new ToolCall("first","get_my_order",Map.of("orderId","99102")))));
        ask(user,id,"查询订单 99102").andExpect(jsonPath("$.code").value("SUCCESS"));
        when(model.nextTurn(any(),any())).thenAnswer(call->{ModelInput input=call.getArgument(0);assertFalse(input.request().question().contains("关联订单编号：99102"));return new ModelTurn("",List.of(new ToolCall("second","get_my_order",Map.of("orderId","99103"))));});
        ask(user,id,"那我再看订单 99103").andExpect(jsonPath("$.code").value("SUCCESS")).andExpect(jsonPath("$.support.orderId").value("99103"));
    }
    @Test void productAlternativesDoNotStayPinnedToThePreviousSingleProduct()throws Exception{
        var user=login("student");String id=session(user,"business");
        when(model.nextTurn(any(),any())).thenReturn(new ModelTurn("",List.of(new ToolCall("first","get_product_detail",Map.of("productId",9001)))));
        ask(user,id,"查询商品 9001").andExpect(jsonPath("$.support.productId").value("9001"));
        when(model.nextTurn(any(),any())).thenReturn(new ModelTurn("",List.of(new ToolCall("alternatives","search_products",Map.of("keyword","Java","page",1,"size",10,"sort","price_asc")))));
        ask(user,id,"还有其他的 Java 书吗").andExpect(jsonPath("$.code").value("SUCCESS")).andExpect(jsonPath("$.trace.tools[0]").value("search_products"));
    }
    @ParameterizedTest
    @ValueSource(strings={"null","[]","not-json","{\"answered\":\"true\",\"answer\":\"x\",\"citedChunkIds\":[]}","{\"answered\":true,\"answer\":7,\"citedChunkIds\":[]}","{\"answered\":true,\"answer\":\"\",\"citedChunkIds\":[]}"})
    void malformedKnowledgeAnswerCannotPublishOrLeaveHalfTurn(String response)throws Exception{
        var user=login("admin");seedKnowledge(user);when(model.chat(any(),anyList())).thenReturn(response);
        ask(user,session(user,"knowledge"),"订单取消规则是什么").andExpect(status().isServiceUnavailable());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages",Integer.class));
        verify(model,never()).nextTurn(any(),any());
    }
    @Test void simultaneousRequestOnOneSessionIsRejectedAndLockRecovers()throws Exception{
        var user=login("student");String id=session(user,"business");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(model.nextTurn(any(),any())).thenAnswer(call->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test release missing");return new ModelTurn("",List.of(new ToolCall("read","list_my_orders",Map.of())));});
        var pool=Executors.newSingleThreadExecutor();
        try {var active=pool.submit(()->{try{return ask(user,id,"查询我的订单").andReturn().getResponse().getStatus();}catch(Exception error){throw new RuntimeException(error);}});
            assertTrue(entered.await(3,TimeUnit.SECONDS));ask(user,id,"查询我的订单").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_BUSY"));
            release.countDown();assertEquals(200,active.get(5,TimeUnit.SECONDS));ask(user,id,"查询我的订单").andExpect(status().isOk());
            assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages",Integer.class));verify(model,times(2)).nextTurn(any(),any());
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void legacyAssistantEndpointCannotBypassPlatformScopeGuard()throws Exception{
        var user=login("student");
        mvc.perform(post("/api/assistant/chat").session(user.session()).header("X-CSRF-TOKEN",user.csrf()).contentType("application/json")
            .content("{\"sessionId\":\"legacy\",\"question\":\"法国首都在哪里\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(model);
    }
    @Test void realCitationCannotLaunderUnrelatedOrInventedAnswer()throws Exception{
        var user=login("admin");seedKnowledge(user);String chunk=jdbc.queryForObject("SELECT id FROM platform_chunks LIMIT 1",String.class);
        when(model.chat(any(),anyList())).thenReturn(json.writeValueAsString(Map.of("answered",true,"answer","巴黎是法国的首都。你的订单退款已经到账。","citedChunkIds",List.of(chunk))));
        ask(user,session(user,"knowledge"),"订单可以取消吗").andExpect(status().isServiceUnavailable())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("巴黎"))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages",Integer.class));
    }
    @Test void knowledgeModeKeepsRelevantBusinessQuestionsOnTheEvidencePath()throws Exception{
        var user=login("admin");seedKnowledge(user);
        when(model.chat(any(),anyList())).thenReturn("{\"answered\":false,\"answer\":\"\",\"citedChunkIds\":[]}");
        ask(user,session(user,"knowledge"),"已经支付的订单怎么处理？").andExpect(status().isOk()).andExpect(jsonPath("$.code").value("INSUFFICIENT_EVIDENCE"));
        verify(model).chat(any(),anyList());verify(model,never()).nextTurn(any(ModelInput.class),any(TimeBudget.class));
    }
    @Test void loginAndMutationsRequireCsrf()throws Exception{
        mvc.perform(post("/api/platform/login").contentType("application/json").content("{\"username\":\"admin\",\"password\":\"VerificationAdmin!2026\"}")).andExpect(status().isForbidden());
        var user=login("admin");mvc.perform(post("/api/platform/sessions").session(user.session()).contentType("application/json").content("{\"mode\":\"business\"}")).andExpect(status().isForbidden());verifyNoInteractions(model);
    }
    @Test void wrongPasswordIsRejectedAndLogoutInvalidatesSession()throws Exception{
        var user=anonymous();mvc.perform(post("/api/platform/login").session(user.session()).header("X-CSRF-TOKEN",user.csrf()).contentType("application/json").content("{\"username\":\"admin\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        user=login("student");mvc.perform(post("/api/platform/logout").session(user.session()).header("X-CSRF-TOKEN",user.csrf())).andExpect(status().isOk());assertTrue(user.session().isInvalid());
    }
    @Test void normalUserCannotImportOrInspectAdminHealth()throws Exception{
        var user=login("student");mvc.perform(get("/api/platform/admin/health").session(user.session())).andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/admin/knowledge/sample").session(user.session()).header("X-CSRF-TOKEN",user.csrf())).andExpect(status().isForbidden());verifyNoInteractions(gateway);
    }
    @Test void sessionsAndMessagesAreOwnerScoped()throws Exception{
        var owner=login("admin");var other=login("student");String id=session(owner,"business");
        ask(owner,id,"你好").andExpect(status().isOk());
        mvc.perform(get("/api/platform/sessions/"+id).session(other.session())).andExpect(status().isNotFound());
        ask(other,id,"窃取其他人的会话").andExpect(status().isNotFound());
        mvc.perform(delete("/api/platform/sessions/"+id).session(other.session()).header("X-CSRF-TOKEN",other.csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/platform/sessions/"+id).session(owner.session())).andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(2));
    }
    @Test void failedModelDoesNotPersistHalfTurnAndNextRequestCanRecover()throws Exception{
        var user=login("admin");String id=session(user,"business");
        when(model.nextTurn(any(ModelInput.class),any(TimeBudget.class))).thenThrow(new ModelCallException("secret upstream detail"));
        ask(user,id,"查询我的订单").andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret upstream"))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages",Integer.class));
        doReturn(new ModelTurn("",List.of(new ToolCall("recovered","list_my_orders",Map.of())))).when(model).nextTurn(any(ModelInput.class),any(TimeBudget.class));
        ask(user,id,"再查一次我的订单").andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("当前账号没有查到")));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages",Integer.class));
    }
    @Test void businessToolsQueryOnlyAuthenticatedUsersDatabaseOrders()throws Exception{
        var admin=login("admin");var student=login("student");
        jdbc.update("INSERT INTO orders(order_id,buyer_id,product_name,status) VALUES (?,?,?,?)",99101,1001,"管理员的订单","PAID");
        jdbc.update("INSERT INTO orders(order_id,buyer_id,product_name,status) VALUES (?,?,?,?)",99102,1002,"学生的订单","PAID");
        when(model.nextTurn(any(ModelInput.class),any(TimeBudget.class))).thenAnswer(call->{
            ModelInput input=call.getArgument(0);
            if(input.exchanges().isEmpty())return new ModelTurn("",List.of(new ToolCall("call-1","list_my_orders",Map.of())));
            return new ModelTurn(json.writeValueAsString(input.exchanges().get(0).results()),List.of());
        });
        ask(student,session(student,"business"),"查询我的订单").andExpect(status().isOk()).andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("学生的订单")))
            .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("管理员的订单"))));
        ask(admin,session(admin,"business"),"查询我的订单").andExpect(status().isOk()).andExpect(jsonPath("$.trace.modelCalls").value(1));
    }
    @Test void tradeCreateCancelAndObjectPermissionUseRealDatabase()throws Exception{
        var admin=login("admin");var student=login("student");String key=UUID.randomUUID().toString();
        var response=mvc.perform(post("/api/trade/orders").session(admin.session()).header("X-CSRF-TOKEN",admin.csrf()).header("Idempotency-Key",key)
            .contentType("application/json").content("{\"productId\":9001,\"quantity\":1}")).andExpect(status().isOk()).andReturn();
        String id=json.readTree(response.getResponse().getContentAsString()).path("orderId").asText();
        assertEquals(4,jdbc.queryForObject("SELECT available_quantity FROM trade_inventory WHERE product_id=9001",Integer.class));
        mvc.perform(get("/api/trade/orders/"+id).session(student.session())).andExpect(status().isNotFound());
        mvc.perform(post("/api/trade/orders/"+id+"/cancel").session(admin.session()).header("X-CSRF-TOKEN",admin.csrf()).contentType("application/json").content("{}")).andExpect(status().isOk());
        assertEquals(5,jdbc.queryForObject("SELECT available_quantity FROM trade_inventory WHERE product_id=9001",Integer.class));
    }
    @Test void invalidQuestionStopsBeforeModel()throws Exception{
        var user=login("admin");String id=session(user,"business");ask(user,id,"x".repeat(4001)).andExpect(status().isBadRequest());verifyNoInteractions(model);
    }
    @Test void malformedJsonIsBadInputAndCannotReachModel()throws Exception{
        var user=login("student");mvc.perform(post("/api/platform/chat").session(user.session()).header("X-CSRF-TOKEN",user.csrf()).contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));verifyNoInteractions(model,gateway);
    }
    @Test void publicSourceRequiresLoginAndDisappearsAfterDeletion()throws Exception{
        var owner=login("admin");seedKnowledge(owner);String chunk=jdbc.queryForObject("SELECT id FROM platform_chunks LIMIT 1",String.class);
        mvc.perform(get("/api/platform/knowledge/chunks/"+chunk)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/platform/knowledge/chunks/"+chunk).session(login("student").session())).andExpect(status().isOk()).andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("库存")));
        String document=jdbc.queryForObject("SELECT id FROM platform_documents LIMIT 1",String.class);
        mvc.perform(delete("/api/platform/admin/knowledge/"+document).session(owner.session()).header("X-CSRF-TOKEN",owner.csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/platform/knowledge/chunks/"+chunk).session(owner.session())).andExpect(status().isNotFound());
    }
    void seedKnowledge(Browser admin)throws Exception{
        mvc.perform(post("/api/platform/admin/knowledge").session(admin.session()).header("X-CSRF-TOKEN",admin.csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("title","取消规则","content","未付款的订单可以取消，取消后库存归还。")))).andExpect(status().isOk());
    }
    @Test void knowledgeCitationsAreFromActualRetrievedChunksAndPersist()throws Exception{
        var user=login("admin");seedKnowledge(user);
        String chunk=jdbc.queryForObject("SELECT id FROM platform_chunks LIMIT 1",String.class);
        when(model.chat(any(),anyList())).thenReturn(json.writeValueAsString(Map.of("answered",true,"answer","未付款的订单可以取消，取消后库存归还。","citedChunkIds",List.of(chunk))));
        String id=session(user,"knowledge");ask(user,id,"订单可以取消吗").andExpect(status().isOk()).andExpect(jsonPath("$.citations[0].source").value("取消规则"));
        mvc.perform(get("/api/platform/sessions/"+id).session(user.session())).andExpect(status().isOk()).andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString(chunk)));
    }
    @Test void inventedCitationIsRejectedAndNoAnswerIsSaved()throws Exception{
        var user=login("admin");seedKnowledge(user);
        when(model.chat(any(),anyList())).thenReturn("{\"answered\":true,\"answer\":\"不可靠答案\",\"citedChunkIds\":[\"invented\"]}");
        ask(user,session(user,"knowledge"),"订单能取消吗").andExpect(status().isServiceUnavailable());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages",Integer.class));
    }
    @Test void insufficientEvidenceDoesNotCallGenerator()throws Exception{
        var user=login("student");ask(user,session(user,"knowledge"),"火星交易规则").andExpect(status().isOk()).andExpect(jsonPath("$.code").value("INSUFFICIENT_EVIDENCE"));verifyNoInteractions(model);
    }
    @Test void embeddingFailureDoesNotPublishDocument()throws Exception{
        var user=login("admin");when(gateway.embed(anyList())).thenThrow(new PlatformException(503,"EMBEDDING_UNAVAILABLE","向量模型暂不可用"));
        mvc.perform(post("/api/platform/admin/knowledge/sample").session(user.session()).header("X-CSRF-TOKEN",user.csrf())).andExpect(status().isServiceUnavailable());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM platform_documents",Integer.class));verifyNoInteractions(vectors);
    }
    @Test void deleteSessionRemovesOnlyOwnersConversation()throws Exception{
        var user=login("admin");String id=session(user,"business");ask(user,id,"你好").andExpect(status().isOk());
        mvc.perform(delete("/api/platform/sessions/"+id).session(user.session()).header("X-CSRF-TOKEN",user.csrf())).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages",Integer.class));
    }
    @Test void completedTraceIsPersistedAndHasTheSameRequestIdentity()throws Exception{
        var user=login("admin");var response=ask(user,session(user,"business"),"查询商品").andExpect(status().isOk())
            .andExpect(jsonPath("$.trace.steps[0].status").value("SUCCESS")).andReturn();
        var result=json.readTree(response.getResponse().getContentAsString());
        assertEquals(result.path("requestId").asText(),result.path("trace").path("traceId").asText());
        var stored=json.readTree(jdbc.queryForObject("SELECT detail_json FROM platform_runs WHERE request_id=?",String.class,result.path("requestId").asText()));
        assertEquals("SUCCESS",stored.path("steps").get(0).path("status").asText());
    }
    @Test void anotherBuyersOrderContextIsRejectedBeforeModelCall()throws Exception{
        var owner=login("admin");var response=mvc.perform(post("/api/trade/orders").session(owner.session()).header("X-CSRF-TOKEN",owner.csrf())
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content("{\"productId\":\"9001\",\"quantity\":1}"))
            .andExpect(status().isOk()).andReturn();String orderId=json.readTree(response.getResponse().getContentAsString()).path("orderId").asText();
        var other=login("student");String session=session(other,"business");
        mvc.perform(post("/api/platform/chat").session(other.session()).header("X-CSRF-TOKEN",other.csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("sessionId",session,"question","查询订单","orderId",orderId)))).andExpect(status().isNotFound());
        verifyNoInteractions(model);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM platform_runs",Integer.class));
    }
    @Test void orderContextReachesModelWithoutChangingVisibleQuestionOrTitle()throws Exception{
        var owner=login("admin");var response=mvc.perform(post("/api/trade/orders").session(owner.session()).header("X-CSRF-TOKEN",owner.csrf())
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content("{\"productId\":\"9001\",\"quantity\":1}"))
            .andExpect(status().isOk()).andReturn();String orderId=json.readTree(response.getResponse().getContentAsString()).path("orderId").asText();
        String session=session(owner,"business"),question="查询这笔订单";
        mvc.perform(post("/api/platform/chat").session(owner.session()).header("X-CSRF-TOKEN",owner.csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("sessionId",session,"question",question,"orderId",orderId))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(orderId));
        verify(model).nextTurn(argThat((ModelInput input)->input.request().question().contains(orderId)),any(TimeBudget.class));
        mvc.perform(get("/api/platform/sessions/"+session).session(owner.session())).andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[0].content").value(question));
        assertEquals(question,jdbc.queryForObject("SELECT title FROM platform_sessions WHERE session_id=?",String.class,session));
    }
    @Test void regularUserCannotReadApplicationWideGatewayRequests()throws Exception{
        var user=login("student");mvc.perform(get("/api/platform/admin/gateway/requests").session(user.session())).andExpect(status().isForbidden());
        verify(gateway,never()).request(eq("/api/gateway/platform/observations"),isNull());
    }
    @Test void concurrentDeleteCannotRemoveAnActiveConversation()throws Exception{
        var user=login("admin");String id=session(user,"business");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(model.nextTurn(any(ModelInput.class),any(TimeBudget.class))).thenAnswer(call->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");return new ModelTurn("",List.of(new ToolCall("locked","list_my_orders",Map.of())));});
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try{var future=executor.submit(()->{try{return ask(user,id,"查询我的订单").andReturn().getResponse().getStatus();}catch(Exception e){throw new RuntimeException(e);}});
            assertTrue(entered.await(3,TimeUnit.SECONDS));mvc.perform(delete("/api/platform/sessions/"+id).session(user.session()).header("X-CSRF-TOKEN",user.csrf())).andExpect(status().isConflict());
            release.countDown();assertEquals(200,future.get(5,TimeUnit.SECONDS));
        }finally{release.countDown();executor.shutdownNow();}
    }
}
