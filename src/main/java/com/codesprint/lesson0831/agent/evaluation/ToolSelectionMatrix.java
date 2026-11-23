package com.codesprint.lesson0831.agent.evaluation;

import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Action;

import java.util.List;

public final class ToolSelectionMatrix {

    private static final List<ToolSelectionCase> STANDARD_CASES =
            List.of(
                    new ToolSelectionCase(
                            "search-price-java",
                            "帮我找50元以下的Java书",
                            Action.SEARCH_PRODUCTS,
                            "需要按照关键词和价格搜索候选商品"
                    ),
                    new ToolSelectionCase(
                            "search-keyword-keyboard",
                            "搜索无线机械键盘",
                            Action.SEARCH_PRODUCTS,
                            "用户要求搜索一类商品"
                    ),
                    new ToolSelectionCase(
                            "search-category-spring",
                            "有哪些Spring Boot相关商品",
                            Action.SEARCH_PRODUCTS,
                            "用户需要获得符合关键词的商品列表"
                    ),

                    new ToolSelectionCase(
                            "detail-product-id",
                            "查看商品P1001的详细信息",
                            Action.GET_PRODUCT_DETAIL,
                            "用户已经指定具体商品，需要查询详情"
                    ),
                    new ToolSelectionCase(
                            "detail-stock",
                            "商品P1002现在还有库存吗",
                            Action.GET_PRODUCT_DETAIL,
                            "库存问题指向一个确定商品"
                    ),
                    new ToolSelectionCase(
                            "detail-description",
                            "介绍一下P1003的价格和描述",
                            Action.GET_PRODUCT_DETAIL,
                            "需要读取确定商品的详细字段"
                    ),

                    new ToolSelectionCase(
                            "orders-list",
                            "查看我的订单",
                            Action.LIST_MY_ORDERS,
                            "需要查询当前登录用户的订单"
                    ),
                    new ToolSelectionCase(
                            "orders-active",
                            "我有哪些未取消订单",
                            Action.LIST_MY_ORDERS,
                            "需要先取得当前用户订单数据"
                    ),
                    new ToolSelectionCase(
                            "orders-history",
                            "我以前买过哪些商品",
                            Action.LIST_MY_ORDERS,
                            "需要从当前用户订单中查询购买记录"
                    ),

                    new ToolSelectionCase(
                            "none-greeting",
                            "你好",
                            Action.NONE,
                            "普通问候不需要业务工具"
                    ),
                    new ToolSelectionCase(
                            "none-java-knowledge",
                            "Java里的继承是什么",
                            Action.NONE,
                            "通用知识问题不需要商品或订单工具"
                    ),
                    new ToolSelectionCase(
                            "none-tool-name",
                            "search_products这个工具名是什么意思",
                            Action.NONE,
                            "只是讨论工具名称，不应执行商品业务工具"
                    )
            );

    private ToolSelectionMatrix() {
    }

    public static List<ToolSelectionCase> standardCases() {
        return STANDARD_CASES;
    }
}