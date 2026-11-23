package com.codesprint.lesson0831.platform;

import com.codesprint.lesson0831.agent.toolcalling.ToolExecutionResult;
import com.codesprint.lesson0831.order.OrderSummary;
import com.codesprint.lesson0831.platform.commerce.CommerceService;
import com.codesprint.lesson0831.product.ProductDetail;
import com.codesprint.lesson0831.product.ProductSummary;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Customer-visible facts come from typed business results, never from model prose. */
@Component
@Profile("platform")
public class SupportAnswerRenderer {
  public record Answer(String text, String code, Map<String, Object> context) {}
  private final CommerceService commerce;
  private static final Map<String, String> ORDER_STATES = Map.of(
      "PENDING_PAYMENT", "待付款", "PAID", "已支付", "CANCELLED", "已取消", "REFUNDED", "已退款");
  private static final Map<String, String> RETURN_STATES = Map.of(
      "REQUESTED", "待审核", "APPROVED", "待寄回", "RETURNED", "待收货", "REFUNDED", "已退款", "REJECTED", "已驳回", "CANCELLED", "已撤销");
  public SupportAnswerRenderer(CommerceService commerce) { this.commerce = commerce; }

  public Answer render(List<ToolExecutionResult> results, SupportScopePolicy.Topic topic) {
    List<String> sections = new ArrayList<>();
    Map<String, Object> context = new LinkedHashMap<>();
    Set<String> orderIds = new HashSet<>(), productIds = new HashSet<>();
    context.put("topic", topic.name());
    for (var execution : results) {
      var result = execution.result();
      if (result == null) return unverified();
      if (!result.success()) {
        if (Set.of("ORDER_NOT_FOUND", "PRODUCT_NOT_FOUND", "NOT_FOUND").contains(result.code())) {
          sections.add("没有找到可查询的记录。请核对编号；订单只支持当前登录账号本人查询。");
          continue;
        }
        if ("INVALID_ARGUMENT".equals(result.code())) return new Answer(SupportScopePolicy.CLARIFY_MESSAGE, "NEEDS_CLARIFICATION", Map.of());
        throw new PlatformException(503, "BUSINESS_QUERY_FAILED", "业务查询暂未完成，请稍后重试。");
      }
      Object data = result.data();
      if (data instanceof OrderSummary order && topic == SupportScopePolicy.Topic.ORDERS) {
        sections.add(order(order)); orderIds.add(Long.toString(order.orderId()));
      } else if (data instanceof ProductDetail product && topic == SupportScopePolicy.Topic.PRODUCTS) {
        sections.add(product(product.id(), product.title(), product.price())
            + "\n\n商品说明：\n> " + safe(product.description(), 600).replace("\n", "\n> "));
        productIds.add(Long.toString(product.id()));
      } else if (data instanceof List<?> items) {
        if (items.isEmpty()) { sections.add(topic == SupportScopePolicy.Topic.ORDERS ? "当前账号没有查到符合条件的订单。指定已取消订单时，请提供订单编号或从订单页发起查询。" : "平台暂时没有找到匹配的在售商品，可以换一个商品名称或关键词。"); continue; }
        if (items.stream().anyMatch(item -> topic == SupportScopePolicy.Topic.PRODUCTS
            ? !(item instanceof ProductSummary) : !(item instanceof OrderSummary))) return unverified();
        List<String> lines = new ArrayList<>();
        for (Object item : items.stream().limit(8).toList()) {
          if (item instanceof ProductSummary product && topic == SupportScopePolicy.Topic.PRODUCTS) lines.add(product(product.id(), product.title(), product.price()));
          else if (item instanceof OrderSummary order && topic == SupportScopePolicy.Topic.ORDERS) lines.add(order(order));
          else return unverified();
        }
        sections.add(String.join("\n\n---\n\n", lines));
        if (items.size() > 8) sections.add("以上显示前 8 条记录，可以补充编号进一步查询。");
        for (Object item : items) {
          if (item instanceof ProductSummary product) productIds.add(Long.toString(product.id()));
          if (item instanceof OrderSummary order) orderIds.add(Long.toString(order.orderId()));
        }
      } else return unverified();
    }
    if (sections.isEmpty()) return unverified();
    if (orderIds.size() == 1) context.put("orderId", orderIds.iterator().next());
    if (productIds.size() == 1) context.put("productId", productIds.iterator().next());
    return new Answer(String.join("\n\n", sections), "SUCCESS", Map.copyOf(context));
  }
  private String product(long id, String title, BigDecimal price) {
    String stock = "";
    try {
      var current = commerce.product(id);
      stock = current.available() ? "，当前可售 " + current.availableQuantity() + " 件" : "，目前已下架";
    } catch (PlatformException missing) {
      if (missing.status() != 404) throw missing;
    }
    return "**" + safe(title, 120) + "**\n\n商品编号：`" + id + "`；售价：**¥ " + price.toPlainString() + "**" + stock + "。\n可在“商品与订单”查看并确认购买。";
  }
  private String order(OrderSummary order) {
    String result = "**" + safe(order.productName(), 120) + "**\n\n订单编号：`" + order.orderId() + "`；当前状态：**" + ORDER_STATES.getOrDefault(order.status(), "待核实") + "**。";
    Map<String, Object> after = order.afterSales();
    if (after != null) {
      String status = String.valueOf(after.get("status"));
      result += "\n\n售后进度：**" + RETURN_STATES.getOrDefault(status, "待核实") + "**。";
      if (after.get("amount") != null) result += "申请金额：¥ " + safe(String.valueOf(after.get("amount")), 40) + "。";
      result += switch(status) {
        case "REQUESTED" -> "请等待管理员审核，可在售后中心查看或撤销申请。";
        case "APPROVED" -> "审核已通过，请在售后中心登记寄回物流。";
        case "RETURNED" -> "寄回信息已登记，请等待管理员确认收货。平台没有接入物流实时轨迹。";
        case "REFUNDED" -> "退款流程已完成，可查看售后处理记录。";
        case "REJECTED", "CANCELLED" -> "可查看处理原因，需要时重新提交申请。";
        default -> "请在售后中心核对处理记录。";
      };
      if (Boolean.TRUE.equals(after.get("refundIsSimulated"))) result += "\n\n这是本地模拟退款，不发生真实资金转账，无需查询支付账户到账。";
    } else result += switch(order.status()) {
      case "PENDING_PAYMENT" -> "\n\n可在订单详情确认模拟付款或取消订单，需要你亲自确认操作。";
      case "PAID" -> "\n\n需要售后时，可在订单详情点击“申请 / 查看售后”。";
      case "CANCELLED" -> "\n\n这笔订单已关闭；需要购买时请重新下单。";
      case "REFUNDED" -> "\n\n可在售后中心查看退款处理记录。本平台退款为模拟操作，不发生真实资金转账。";
      default -> "\n\n请在订单详情核对状态。";
    };
    return result;
  }
  public static Answer unverified() { return new Answer("这次没有取得可核实的业务结果，我无法据此回答。请说明商品名称、提供本人订单编号，或从订单详情发起查询。", "UNVERIFIED_ANSWER", Map.of()); }
  static String safe(String value, int max) {
    if (value == null) return "";
    String text = value.length() > max ? value.substring(0, max) + "…" : value;
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\\", "\\\\").replace("`", "\\`").replace("*", "\\*").replace("_", "\\_")
        .replace("[", "\\[").replace("]", "\\]").replace("#", "\\#");
  }
}
