package com.codesprint.lesson0831.order;

import java.util.List;

public interface OrderService {
    List<OrderSummary> listMyOrders(Long currentUserId);

    default java.util.Optional<OrderSummary> findMyOrder(Long currentUserId, long orderId) {
        return listMyOrders(currentUserId).stream().filter(order -> order.orderId() == orderId).findFirst();
    }
}
