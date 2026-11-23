package com.codesprint.lesson0831.order;

import com.codesprint.lesson0831.common.BizException;
import com.codesprint.lesson0831.product.ToolResult;
import com.codesprint.lesson0831.security.CurrentUserContext;

import java.util.List;

public class OrderTools {

    private final CurrentUserContext currentUserContext;
    private final OrderService orderService;

    public OrderTools(
            CurrentUserContext currentUserContext,
            OrderService orderService
    ) {
        this.currentUserContext = currentUserContext;
        this.orderService = orderService;
    }

    public ToolResult<List<OrderSummary>> listMyOrders() {
        Long currentUserId =
                currentUserContext.getUserId();

        if (currentUserId == null) {
            return ToolResult.failure(
                    "UNAUTHORIZED",
                    "请先登录"
            );
        }

        try {
            List<OrderSummary> orders =
                    orderService.listMyOrders(
                            currentUserId
                    );

            return ToolResult.success(orders);
        } catch (BizException exception) {
            return ToolResult.failure(
                    exception.getCode(),
                    exception.getMessage()
            );
        }
    }

    public ToolResult<OrderSummary> getMyOrder(long orderId) {
        Long user = currentUserContext.getUserId();
        if (user == null) return ToolResult.failure("UNAUTHORIZED", "请先登录");
        if (orderId <= 0) return ToolResult.failure("INVALID_ARGUMENT", "订单编号无效");
        try {
            return orderService.findMyOrder(user, orderId)
                    .map(ToolResult::success)
                    .orElseGet(() -> ToolResult.failure("ORDER_NOT_FOUND", "订单不存在"));
        } catch (BizException failure) {
            return ToolResult.failure(failure.getCode(), failure.getMessage());
        }
    }
}
