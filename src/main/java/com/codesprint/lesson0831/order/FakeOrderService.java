package com.codesprint.lesson0831.order;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Profile("(dev | test) & !trade-demo")
public class FakeOrderService implements OrderService {

    private final List<OrderSummary> orders = List.of(
            new OrderSummary(
                    1L,
                    1001L,
                    "Java编程思想",
                    "PAID"
            ),
            new OrderSummary(
                    2L,
                    1002L,
                    "数据结构",
                    "CREATED"
            )
    );

    @Override
    public List<OrderSummary> listMyOrders(
            Long currentUserId
    ) {
        if (currentUserId == null) {
            throw new IllegalArgumentException(
                    "当前用户身份信息不能为空"
            );
        }

        List<OrderSummary> result =
                new ArrayList<>();

        for (OrderSummary order : orders) {
            if (currentUserId.equals(order.buyerId())) {
                result.add(order);
            }
        }

        return result;
    }
}
