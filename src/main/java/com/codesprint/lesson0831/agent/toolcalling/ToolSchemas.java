package com.codesprint.lesson0831.agent.toolcalling;

import java.util.List;
import java.util.Map;

public class ToolSchemas {

    public static List<Map<String, Object>> all() {
        return List.of(
                function(
                        "search_products",
                        "按关键词搜索商品，支持分页和排序。只读操作。",
                        Map.of(
                                "keyword", Map.of(
                                        "type", "string",
                                        "description", "非空搜索关键词"
                                ),
                                "page", Map.of(
                                        "type", "integer",
                                        "minimum", 1,
                                        "maximum", Integer.MAX_VALUE
                                ),
                                "size", Map.of(
                                        "type", "integer",
                                        "minimum", 1,
                                        "maximum", Integer.MAX_VALUE
                                ),
                                "sort", Map.of(
                                        "type", "string",
                                        "enum", List.of(
                                                "title_asc", "title_desc",
                                                "price_asc", "price_desc"
                                        )
                                )
                        ),
                        List.of("keyword", "page", "size", "sort")
                ),
                function(
                        "get_product_detail",
                        "根据商品编号查询可见商品详情。只读操作。",
                        Map.of(
                                "productId", Map.of(
                                        "type", "integer",
                                        "minimum", 1,
                                        "maximum", Long.MAX_VALUE
                                )
                        ),
                        List.of("productId")
                ),
                function(
                        "list_my_orders",
                        "查询当前登录用户未取消的订单。查询指定订单（包括已取消订单）时使用 get_my_order。身份由服务器获取。",
                        Map.of(),
                        List.of()
                ),
                function(
                        "get_my_order",
                        "按编号查询本人订单的当前状态和售后进度，包括待付款、已支付、已取消、已退款。身份由服务器获取。只读操作。"
                                + "若结果 afterSales.refundIsSimulated=true，必须明确这是本地模拟退款，没有真实资金转账；"
                                + "不要建议查询支付账户、银行卡到账或联系支付机构。后续只能建议查看本平台订单和售后处理记录。",
                        Map.of("orderId", Map.of("type", "string", "pattern", "^[1-9][0-9]{0,18}$",
                                "description", "完整订单编号，使用字符串避免长整数精度丢失")),
                        List.of("orderId")
                )
        );
    }

    private static Map<String, Object> function(
            String name,
            String description,
            Map<String, Object> properties,
            List<String> required
    ) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", Map.of(
                                "type", "object",
                                "properties", properties,
                                "required", required,
                                "additionalProperties", false
                        )
                )
        );
    }
}
