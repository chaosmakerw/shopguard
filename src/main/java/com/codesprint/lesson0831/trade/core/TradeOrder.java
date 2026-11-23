package com.codesprint.lesson0831.trade.core;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeOrder(long orderId, long buyerId, long productId, String productName,
                         int quantity, BigDecimal totalAmount, String status,
                         Instant createdAt, Instant expiresAt) {
}
