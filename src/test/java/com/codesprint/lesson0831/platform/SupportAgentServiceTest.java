package com.codesprint.lesson0831.platform;

import com.codesprint.lesson0831.agent.*;
import com.codesprint.lesson0831.agent.toolcalling.*;
import com.codesprint.lesson0831.platform.commerce.CommerceService;
import com.codesprint.lesson0831.product.*;
import java.time.Duration;
import java.math.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import com.codesprint.lesson0831.observability.AgentTrace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real planner/executor/renderer; controlled model and dispatcher. No Spring, database or network. */
class SupportAgentServiceTest {
  ChatModel model;
  ToolDispatcher dispatcher;
  CommerceService commerce;
  SupportAgentService support;
  @BeforeEach void setup() {
    model=mock(ChatModel.class);dispatcher=mock(ToolDispatcher.class);commerce=mock(CommerceService.class);
    support=new SupportAgentService(model,new ToolCallExecutor(dispatcher),new SupportAnswerRenderer(commerce),Duration.ofSeconds(10));
    doReturn(ToolResult.success(List.of())).when(dispatcher).dispatch(anyString(),anyMap());
  }
  SupportAnswerRenderer.Answer answer(SupportScopePolicy.Topic topic,Map<String,Object> context) {
    return support.answer(1002,"controlled","查询当前业务",topic,context,List.of(),null,false);
  }
  void plan(ToolCall... calls) { when(model.nextTurn(any(),any())).thenReturn(new ModelTurn("MODEL_DRAFT_MUST_NOT_LEAK",List.of(calls))); }
  @Test void malformedArgumentsInvalidateTheWholePlanBeforeAnyToolRuns() {
    plan(new ToolCall("bad","get_my_order",Map.of("orderId","1.5")),new ToolCall("next","list_my_orders",Map.of()));
    assertEquals("UNVERIFIED_ANSWER",answer(SupportScopePolicy.Topic.ORDERS,Map.of()).code());
    verifyNoInteractions(dispatcher,commerce);
  }
  @Test void blankAndDuplicateCallIdsAreRejectedWithoutUserInputError() {
    for(String second:List.of("", "same")) {
      plan(new ToolCall("same","list_my_orders",Map.of()),new ToolCall(second,"list_my_orders",Map.of()));
      assertEquals("UNVERIFIED_ANSWER",answer(SupportScopePolicy.Topic.ORDERS,Map.of()).code());
    }
    verifyNoInteractions(dispatcher,commerce);
  }
  @Test void productFollowUpCannotSilentlySwitchToAnotherProduct() {
    plan(new ToolCall("other","get_product_detail",Map.of("productId",9002)));
    assertEquals("UNVERIFIED_ANSWER",answer(SupportScopePolicy.Topic.PRODUCTS,Map.of("topic","PRODUCTS","productId","9001")).code());
    verifyNoInteractions(dispatcher,commerce);
  }
  @Test void toolFailureStopsTheRemainingPlan() {
    plan(new ToolCall("first","list_my_orders",Map.of()),new ToolCall("next","get_my_order",Map.of("orderId","99102")));
    doReturn(ToolResult.failure("INTERNAL_ERROR","private database failure")).when(dispatcher).dispatch(eq("list_my_orders"),anyMap());
    var failure=assertThrows(PlatformException.class,()->answer(SupportScopePolicy.Topic.ORDERS,Map.of()));
    assertEquals("BUSINESS_QUERY_FAILED",failure.code());assertFalse(failure.getMessage().contains("private"));
    verify(dispatcher,never()).dispatch(eq("get_my_order"),anyMap());
    verifyNoInteractions(commerce);
  }
  static Stream<ToolCall> unsafePlans() {
    return Stream.of(
        new ToolCall("a","get_my_order",Map.of("orderId",99102L)),
        new ToolCall("a","get_my_order",Map.of("orderId","9223372036854775808")),
        new ToolCall("a","get_my_order",Map.of("orderId","1e3")),
        new ToolCall("a","get_my_order",Map.of("orderId","99102","userId",1001)),
        new ToolCall("a","list_my_orders",Map.of("buyerId",1001)),
        new ToolCall("a","list_my_orders",null),
        new ToolCall("a","cancel_order",Map.of("orderId","99102")),
        new ToolCall("a","get_product_detail",Map.of("productId",9001.0)),
        new ToolCall("a","get_product_detail",Map.of("productId",new BigDecimal("9001.5"))),
        new ToolCall("a","get_product_detail",Map.of("productId",BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE))),
        new ToolCall(null,"list_my_orders",Map.of()),
        new ToolCall("a",null,Map.of()));
  }
  @ParameterizedTest @MethodSource("unsafePlans")
  void rawNumbersIdentityFieldsAndUnknownToolsAreRejectedBeforeConversion(ToolCall call) {
    plan(call);
    var topic="get_product_detail".equals(call.name())?SupportScopePolicy.Topic.PRODUCTS:SupportScopePolicy.Topic.ORDERS;
    assertEquals("UNVERIFIED_ANSWER",answer(topic,Map.of()).code());verifyNoInteractions(dispatcher,commerce);
  }
  @Test void selectedOrderCannotBecomeAnUnscopedListOrAnotherOrder() {
    for(var call:List.of(new ToolCall("list","list_my_orders",Map.of()),new ToolCall("other","get_my_order",Map.of("orderId","99103")))) {
      plan(call);assertEquals("UNVERIFIED_ANSWER",answer(SupportScopePolicy.Topic.ORDERS,Map.of("orderId","99102")).code());
    }
    verifyNoInteractions(dispatcher);
  }
  @Test void fourReadToolsAreAllowedButFiveAreRejectedAsAWhole() {
    plan(Stream.iterate(1,n->n+1).limit(4).map(n->new ToolCall("call-"+n,"list_my_orders",Map.of())).toArray(ToolCall[]::new));
    assertEquals("SUCCESS",answer(SupportScopePolicy.Topic.ORDERS,Map.of()).code());verify(dispatcher,times(4)).dispatch(anyString(),anyMap());
    clearInvocations(dispatcher);
    plan(Stream.iterate(1,n->n+1).limit(5).map(n->new ToolCall("call-"+n,"list_my_orders",Map.of())).toArray(ToolCall[]::new));
    assertEquals("UNVERIFIED_ANSWER",answer(SupportScopePolicy.Topic.ORDERS,Map.of()).code());verifyNoInteractions(dispatcher);
  }
  @Test void modelDeadlinePreventsAllDownstreamQueries() {
    var clock=new AtomicLong();support=new SupportAgentService(model,new ToolCallExecutor(dispatcher),new SupportAnswerRenderer(commerce),Duration.ofNanos(10),clock::get);
    when(model.nextTurn(any(),any())).thenAnswer(call->{clock.set(10);return new ModelTurn("",List.of(new ToolCall("late","list_my_orders",Map.of())));});
    assertThrows(ModelCallException.class,()->answer(SupportScopePolicy.Topic.ORDERS,Map.of()));verifyNoInteractions(dispatcher,commerce);
  }
  @Test void toolDeadlineStopsTheNextToolWithoutTimingSleeps() {
    var clock=new AtomicLong();support=new SupportAgentService(model,new ToolCallExecutor(dispatcher),new SupportAnswerRenderer(commerce),Duration.ofNanos(10),clock::get);
    plan(new ToolCall("a","list_my_orders",Map.of()),new ToolCall("b","get_my_order",Map.of("orderId","99102")));
    doAnswer(call->{clock.set(10);return ToolResult.success(List.of());}).when(dispatcher).dispatch(eq("list_my_orders"),anyMap());
    assertThrows(ModelCallException.class,()->answer(SupportScopePolicy.Topic.ORDERS,Map.of()));verify(dispatcher,never()).dispatch(eq("get_my_order"),anyMap());
  }
  @Test void verifiedAnswerKeepsUsageAndTraceButNeverDraftText() {
    List<String> events=new ArrayList<>();
    when(model.nextTurn(any(),any())).thenAnswer(call->{AgentTrace.event("delta","PRIVATE_DRAFT");AgentTrace.usage(17,9);return new ModelTurn("PRIVATE_DRAFT",List.of(new ToolCall("safe","list_my_orders",Map.of())));});
    try(var trace=new AgentTrace("owned-trace",true,null)) {
      var result=support.answer(1002,"s","查询订单",SupportScopePolicy.Topic.ORDERS,Map.of(),List.of(),(type,data)->events.add(type+":"+data),true);
      assertFalse(result.text().contains("PRIVATE_DRAFT"));assertTrue(events.stream().noneMatch(e->e.contains("PRIVATE_DRAFT")));
      assertEquals(1,trace.snapshot().get("modelCalls"));assertEquals(17L,trace.snapshot().get("inputTokens"));
      assertEquals(true,trace.snapshot().get("usageComplete"));assertEquals("owned-trace",AgentTrace.traceId());
    }
  }
  @Test void exceptionsRestoreTraceContextAndNextRequestCanRun() {
    when(model.nextTurn(any(),any())).thenThrow(new ModelCallException("controlled"));
    try(var parent=new AgentTrace("outer-trace",false,null)) {
      assertThrows(ModelCallException.class,()->answer(SupportScopePolicy.Topic.ORDERS,Map.of()));assertEquals("outer-trace",AgentTrace.traceId());
      doReturn(new ModelTurn("",List.of(new ToolCall("recover","list_my_orders",Map.of())))).when(model).nextTurn(any(),any());
      assertEquals("SUCCESS",answer(SupportScopePolicy.Topic.ORDERS,Map.of()).code());assertEquals(2,parent.snapshot().get("modelCalls"));
    }
  }
}
