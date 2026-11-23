package com.codesprint.lesson0831.trade.api;

import com.codesprint.lesson0831.trade.core.TradeOrder;
import com.codesprint.lesson0831.trade.core.TradeProduct;
import java.time.Instant;
import java.util.List;

public final class TradeViews {
    private TradeViews() { }

    public record Product(String productId, String title, String description, String price, int availableQuantity) {
        public static Product of(TradeProduct value) {
            return new Product(Long.toString(value.productId()), value.title(), value.description(),
                    value.price().toPlainString(), value.availableQuantity());
        }
    }

    public record Order(String orderId, String productId, String productName, int quantity,
                        String totalAmount, String status, Instant createdAt, Instant expiresAt) {
        public static Order of(TradeOrder value) {
            return new Order(Long.toString(value.orderId()), Long.toString(value.productId()), value.productName(),
                    value.quantity(), value.totalAmount().toPlainString(), value.status(), value.createdAt(), value.expiresAt());
        }
    }

    public record Page<T>(List<T> items, int page, int size) { }
}
