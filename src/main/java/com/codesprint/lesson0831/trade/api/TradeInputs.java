package com.codesprint.lesson0831.trade.api;

import com.codesprint.lesson0831.trade.core.TradeException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

final class TradeInputs {
    private TradeInputs() { }
    static void object(JsonNode node, Set<String> allowed, boolean required) {
        if (node == null && !required) return;
        if (node == null || !node.isObject()) throw bad("请求必须为 JSON 对象");
        node.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw bad("请求包含不支持的字段");
        });
    }
    static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw bad("编号必须为正整数");
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { throw bad("编号超出支持范围"); }
    }
    static long productId(JsonNode node) {
        if (node == null) throw bad("请选择商品");
        if (node.isTextual()) return id(node.textValue());
        if (node.isIntegralNumber() && node.canConvertToLong() && node.longValue() > 0) return node.longValue();
        throw bad("商品编号必须为正整数");
    }
    static int quantity(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) throw bad("购买数量必须为整数");
        int quantity = node.intValue();
        if (quantity < 1 || quantity > 20) throw bad("购买数量必须为 1 至 20");
        return quantity;
    }
    static TradeException bad(String message) { return new TradeException("INVALID_INPUT", 400, message); }
}
