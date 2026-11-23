package com.codesprint.lesson0831.platform.commerce;

import com.codesprint.lesson0831.order.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** The existing read-only order tool also explains the persisted after-sales state. */
@Service
@Primary
@Profile("platform")
public class PlatformOrderService implements OrderService {
  private final JdbcOrderService orders;
  private final CommerceService commerce;

  public PlatformOrderService(DataSource source, CommerceService commerce) {
    this.orders = new JdbcOrderService(source);
    this.commerce = commerce;
  }

  public List<OrderSummary> listMyOrders(Long user) {
    return orders.listMyOrders(user);
  }

  public Optional<OrderSummary> findMyOrder(Long user, long id) {
    return orders
        .findMyOrder(user, id)
        .map(
            order -> {
              var afterSales =
                  commerce
                      .orderReturn(user, id)
                      .map(
                          item ->
                              Map.<String, Object>of(
                                  "id",
                                  item.id(),
                                  "type",
                                  item.type(),
                                  "status",
                                  item.status(),
                                  "amount",
                                  item.amount(),
                                  "decisionNote",
                                  item.decisionNote(),
                                  "refundIsSimulated",
                                  true,
                                  "refundNotice",
                                  "本地模拟退款，不发生真实资金转账。可在本平台查看订单和售后处理记录，无需查询支付账户到账。"))
                      .orElse(null);
              return new OrderSummary(
                  order.orderId(),
                  order.buyerId(),
                  order.productName(),
                  order.status(),
                  afterSales);
            });
  }
}
