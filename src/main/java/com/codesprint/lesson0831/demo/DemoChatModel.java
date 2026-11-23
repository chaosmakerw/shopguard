package com.codesprint.lesson0831.demo;

import com.codesprint.lesson0831.agent.ChatMessage;
import com.codesprint.lesson0831.agent.ChatModel;
import com.codesprint.lesson0831.agent.ChatRequest;
import com.codesprint.lesson0831.agent.toolcalling.ModelInput;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;
import com.codesprint.lesson0831.order.OrderSummary;
import com.codesprint.lesson0831.product.ProductDetail;
import com.codesprint.lesson0831.product.ProductSummary;
import com.codesprint.lesson0831.product.ToolResult;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 确定性协议替身：仅用于演示真实 Dispatcher 与工具结果回传。 */
public final class DemoChatModel implements ChatModel {
    private static final Pattern PRODUCT_ID = Pattern.compile("(?:商品|product)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    @Override
    public String chat(ChatRequest request, List<ChatMessage> history) {
        return "请选择商品搜索、商品详情或我的订单；平台规则请切换到知识库问答。";
    }

    @Override
    public ModelTurn nextTurn(ModelInput input) {
        DemoTrace.add("MODEL", "TURN", "history=" + input.history().size() + ", exchanges=" + input.exchanges().size());
        if (!input.exchanges().isEmpty()) {
            var exchange = input.exchanges().get(input.exchanges().size() - 1);
            ToolResult<?> result = exchange.results().get(0).result();
            DemoTrace.add("TOOL_RESULT", result.code(), "callId=" + exchange.results().get(0).toolCallId());
            DemoTrace.add("MODEL", "FINAL", "工具结果已回传，生成最终文本");
            return new ModelTurn(format(result), List.of());
        }
        String question = input.request().question();
        ToolCall call;
        if (question.contains("订单")) {
            // 固定攻击样例：把伪造身份传到真实 Validator，观察其拒绝。
            Map<String, Object> arguments = question.contains("1002") || question.contains("他人")
                    ? Map.of("buyerId", 1002L) : Map.of();
            call = new ToolCall("demo-order", "list_my_orders", arguments);
        } else if (question.contains("详情")) {
            Matcher matcher = PRODUCT_ID.matcher(question);
            BigInteger id = matcher.find() ? new BigInteger(matcher.group(1)) : BigInteger.ONE;
            call = new ToolCall("demo-detail", "get_product_detail", Map.of("productId", id));
        } else if (question.contains("搜索")) {
            String keyword = question.substring(question.indexOf("搜索") + 2).trim();
            call = new ToolCall("demo-search", "search_products",
                    Map.of("keyword", keyword, "page", 1, "size", 5, "sort", "price_asc"));
        } else {
            DemoTrace.add("MODEL", "FINAL", "本轮无需业务工具");
            return new ModelTurn(chat(input.request(), input.history()), List.of());
        }
        DemoTrace.add("TOOL_CALL", "DISPATCH", call.name() + " / callId=" + call.id());
        return new ModelTurn(null, List.of(call));
    }

    private String format(ToolResult<?> result) {
        if (!result.success()) {
            return "工具拒绝或执行失败 [" + result.code() + "]：" + result.message();
        }
        if (result.data() instanceof ProductDetail product) {
            return "商品 #" + product.id() + " · " + product.title() + "\n价格：¥" + product.price()
                    + "\n" + product.description();
        }
        if (result.data() instanceof List<?> items) {
            if (items.isEmpty()) {
                return "查询成功，暂无匹配结果。";
            }
            StringBuilder answer = new StringBuilder("查询成功：\n");
            for (Object item : items) {
                if (item instanceof ProductSummary product) {
                    answer.append("商品 #").append(product.id()).append(" · ").append(product.title())
                            .append(" · ¥").append(product.price()).append('\n');
                } else if (item instanceof OrderSummary order) {
                    answer.append("订单 #").append(order.orderId()).append(" · ").append(order.productName())
                            .append(" · ").append(order.status()).append('\n');
                }
            }
            return answer.toString().strip();
        }
        return "工具执行成功。";
    }
}
