package com.codesprint.lesson0831.product;

import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import java.math.BigInteger;

public class ToolArgumentValidator {
    private static final Set<String> SEARCH_FIELDS = Set.of(
            "keyword",
            "page",
            "size",
            "sort"
    );

    private static final Set<String> DETAIL_FIELDS = Set.of(
            "productId"
    );

    private boolean isPositiveIntegerWithin(Number value, long maximum) {
        BigInteger integer;

        try {
            if (value instanceof BigInteger bigInteger) {
                integer = bigInteger;
            } else if (value instanceof BigDecimal bigDecimal) {
                integer = bigDecimal.toBigIntegerExact();
            } else if (value instanceof Byte
                    || value instanceof Short
                    || value instanceof Integer
                    || value instanceof Long) {
                integer = BigInteger.valueOf(value.longValue());
            } else {
                return false;
            }
        } catch (ArithmeticException exception) {
            return false;
        }

        return integer.signum() > 0
                && integer.compareTo(BigInteger.valueOf(maximum)) <= 0;
    }
    public ToolResult<Void> validate(String toolName,
                                     Map<String,Object> arguments){
        if ("get_my_order".equals(toolName)) {
            if (arguments == null || !arguments.containsKey("orderId"))
                return ToolResult.failure("MISSING_ARGUMENT", "缺少参数: orderId");
            if (!arguments.keySet().equals(Set.of("orderId")))
                return ToolResult.failure("UNKNOWN_ARGUMENT", "查询订单只接受 orderId，身份由服务器获取");
            Object value = arguments.get("orderId");
            if (!(value instanceof String id) || !id.matches("[1-9][0-9]{0,18}"))
                return ToolResult.failure("INVALID_ARGUMENT", "订单编号必须为正整数的十进制字符串");
            try { Long.parseLong(id); }
            catch (NumberFormatException invalid) { return ToolResult.failure("INVALID_ARGUMENT", "订单编号超出范围"); }
            return ToolResult.success(null);
        }
        if ("list_my_orders".equals(toolName)) {
            if (arguments == null) {
                return ToolResult.failure(
                        "MISSING_ARGUMENT",
                        "工具参数不能为 null，请传入空对象"
                );
            }

            if (!arguments.isEmpty()) {
                return ToolResult.failure(
                        "UNKNOWN_ARGUMENT",
                        "list_my_orders 不接受任何参数"
                );
            }

            return ToolResult.success(null);
        }
        if (!"search_products".equals(toolName)
                && !"get_product_detail".equals(toolName)) {
            return ToolResult.failure(
                    "UNKNOWN_TOOL",
                    "未知工具: " + toolName
            );
        }

        if (arguments == null || arguments.isEmpty()) {
            return ToolResult.failure(
                    "MISSING_ARGUMENT",
                    "工具参数不能为空"
            );
        }
        Set<String> allowedFields;

        if ("search_products".equals(toolName)) {
            allowedFields = SEARCH_FIELDS;
        } else {
            allowedFields = DETAIL_FIELDS;
        }
        for (String field : arguments.keySet()) {
            if (!allowedFields.contains(field)) {
                return ToolResult.failure(
                        "UNKNOWN_ARGUMENT",
                        "存在未知参数: " + field
                );
            }
        }
        if ("search_products".equals(toolName)) {
            String[] requiredFields = {
                    "keyword",
                    "page",
                    "size",
                    "sort"
            };

            for (String field : requiredFields) {
                if (!arguments.containsKey(field)) {
                    return ToolResult.failure(
                            "MISSING_ARGUMENT",
                            "缺少参数: " + field
                    );
                }
            }
        } else {
            if (!arguments.containsKey("productId")) {
                return ToolResult.failure(
                        "MISSING_ARGUMENT",
                        "缺少参数: productId"
                );
            }
        }
        if ("search_products".equals(toolName)) {
            if (!(arguments.get("keyword") instanceof String)) {
                return ToolResult.failure(
                        "INVALID_ARGUMENT",
                        "参数 keyword 必须是字符串"
                );
            }

            if (!(arguments.get("page") instanceof Number page)
                    || !isPositiveIntegerWithin(page, Integer.MAX_VALUE)){
                return ToolResult.failure(
                        "INVALID_ARGUMENT",
                        "参数 page 必须是整数"
                );
            }

            if (!(arguments.get("size") instanceof Number size)
                    || !isPositiveIntegerWithin(size, Integer.MAX_VALUE)){
                return ToolResult.failure(
                        "INVALID_ARGUMENT",
                        "参数 size 必须是整数"
                );
            }

            if (!(arguments.get("sort") instanceof String)) {
                return ToolResult.failure(
                        "INVALID_ARGUMENT",
                        "参数 sort 必须是字符串"
                );
            }
        }
        if ("get_product_detail".equals(toolName)) {
            if (!(arguments.get("productId") instanceof Number productId)) {
                return ToolResult.failure(
                        "INVALID_ARGUMENT",
                        "参数 productId 必须是整数"
                );
            }

            if (!isPositiveIntegerWithin(productId, Long.MAX_VALUE)) {
                return ToolResult.failure(
                        "INVALID_ARGUMENT",
                        "参数 productId 必须大于0"
                );
            }
        }
        return ToolResult.success(null);
    }
}
