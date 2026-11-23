package com.codesprint.lesson0831.platform;

import com.codesprint.lesson0831.agent.toolcalling.ToolExecutionResult;
import com.codesprint.lesson0831.order.OrderSummary;
import com.codesprint.lesson0831.platform.commerce.CommerceService;
import com.codesprint.lesson0831.product.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Typed business output boundary; real rendering, mocked inventory, no database/model/network. */
class SupportAnswerRendererTest {
  CommerceService commerce=mock(CommerceService.class);
  SupportAnswerRenderer renderer=new SupportAnswerRenderer(commerce);
  @BeforeEach void inventory() {
    when(commerce.product(anyLong())).thenAnswer(call->new CommerceService.Product(""+call.getArgument(0),"产品","说明","39.00",true,"书籍",6,0,0,0));
  }
  SupportAnswerRenderer.Answer render(Object data,SupportScopePolicy.Topic topic) {
    return renderer.render(List.of(new ToolExecutionResult("query",ToolResult.success(data))),topic);
  }
  @Test void productTextIsEscapedAndInventoryComesFromBusinessService() {
    var product=new ProductDetail(9001,"<img src=x onerror=alert(1)>","[点击](javascript:alert(1))\n<script>bad</script>",new BigDecimal("39.00"),true);
    var result=render(product,SupportScopePolicy.Topic.PRODUCTS);
    assertEquals("SUCCESS",result.code());assertTrue(result.text().contains("当前可售 6 件"));
    assertFalse(result.text().contains("<img"));assertFalse(result.text().contains("<script>"));assertTrue(result.text().contains("\\[点击\\]"));
    assertEquals("9001",result.context().get("productId"));verify(commerce).product(9001);
  }
  @ParameterizedTest @CsvSource(delimiter='|',value={"REQUESTED|等待管理员审核|退款流程已完成","APPROVED|登记寄回物流|退款流程已完成","RETURNED|等待管理员确认收货|退款流程已完成","REFUNDED|退款流程已完成|查询支付账户到账。"})
  void afterSalesStateExplainsTheActualNextAction(String state,String action,String forbidden) {
    var order=new OrderSummary(99102L,1002L,"Java 书","PAID",Map.of("status",state,"amount","39.00","refundIsSimulated",true));
    var result=render(order,SupportScopePolicy.Topic.ORDERS);
    assertTrue(result.text().contains(action));assertTrue(result.text().contains("本地模拟退款"));assertTrue(result.text().contains("不发生真实资金转账"));
    if(!"REFUNDED".equals(state))assertFalse(result.text().contains(forbidden));
  }
  @Test void arbitraryObjectsCannotBecomeModelLikeFreeText() {
    var result=render(Map.of("answer","UNTRUSTED_PAYLOAD"),SupportScopePolicy.Topic.PRODUCTS);
    assertEquals("UNVERIFIED_ANSWER",result.code());assertFalse(result.text().contains("UNTRUSTED_PAYLOAD"));verifyNoInteractions(commerce);
  }
  @Test void malformedItemsBeyondDisplayLimitInvalidateTheWholeResult() {
    var items=new ArrayList<Object>();for(int i=0;i<8;i++)items.add(new ProductSummary(9001+i,"book-"+i,BigDecimal.ONE));items.add(Map.of("answer","BAD"));
    assertEquals("UNVERIFIED_ANSWER",render(items,SupportScopePolicy.Topic.PRODUCTS).code());verifyNoInteractions(commerce);
  }
  @Test void resultSizeIsBoundedAndAnAmbiguousListDoesNotSelectOneEntity() {
    var items=IntStream.range(0,20).mapToObj(i->new ProductSummary(9001+i,"book-"+i,BigDecimal.ONE)).toList();
    var result=render(items,SupportScopePolicy.Topic.PRODUCTS);
    assertEquals("SUCCESS",result.code());assertFalse(result.text().contains("book-8"));assertFalse(result.context().containsKey("productId"));
    verify(commerce,times(8)).product(anyLong());
  }
  @Test void unknownOrderStateIsNotRepeatedAsAnAuthoritativeBusinessClaim() {
    var result=render(new OrderSummary(99102L,1002L,"Book","REFUND_ALREADY_ARRIVED<script>",null),SupportScopePolicy.Topic.ORDERS);
    assertTrue(result.text().contains("待核实"));assertFalse(result.text().contains("ALREADY_ARRIVED"));
  }
}
