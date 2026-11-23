package com.codesprint.lesson0831.order;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JdbcOrderService
        implements OrderService {

    private final DataSource dataSource;

    public JdbcOrderService(
            DataSource dataSource
    ) {
        this.dataSource =
                Objects.requireNonNull(
                        dataSource,
                        "DataSource 不能为空"
                );
    }

    @Override
    public java.util.Optional<OrderSummary> findMyOrder(Long currentUserId, long orderId) {
        if (currentUserId == null || currentUserId <= 0 || orderId <= 0) {
            throw new IllegalArgumentException("用户编号和订单编号必须为正整数");
        }
        String sql = "SELECT order_id, buyer_id, product_name, status FROM orders WHERE buyer_id = ? AND order_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, currentUserId);
            statement.setLong(2, orderId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return java.util.Optional.empty();
                return java.util.Optional.of(new OrderSummary(row.getLong("order_id"), row.getLong("buyer_id"),
                        row.getString("product_name"), row.getString("status")));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("数据库查询失败", failure);
        }
    }

    @Override
    public List<OrderSummary> listMyOrders(
            Long currentUserId
    ) {
        if (currentUserId == null) {
            throw new IllegalArgumentException(
                    "当前用户身份信息不能为空"
            );
        }

        if (currentUserId <= 0) {
            throw new IllegalArgumentException(
                    "当前用户编号必须大于0"
            );
        }

        String sql = """
                SELECT
                    order_id,
                    buyer_id,
                    product_name,
                    status
                FROM orders
                WHERE buyer_id = ?
                  AND status <> 'CANCELLED'
                ORDER BY order_id ASC
                """;

        List<OrderSummary> orders =
                new ArrayList<>();

        try (
                Connection connection =
                        dataSource.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(
                    1,
                    currentUserId
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                while (resultSet.next()) {
                    Long orderId =
                            resultSet.getLong(
                                    "order_id"
                            );

                    Long buyerId =
                            resultSet.getLong(
                                    "buyer_id"
                            );

                    String productName =
                            resultSet.getString(
                                    "product_name"
                            );

                    String status =
                            resultSet.getString(
                                    "status"
                            );

                    orders.add(
                            new OrderSummary(
                                    orderId,
                                    buyerId,
                                    productName,
                                    status
                            )
                    );
                }
            }

            return List.copyOf(orders);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "数据库查询失败",
                    exception
            );
        }
    }
}
