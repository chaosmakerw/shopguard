package com.codesprint.lesson0831.trade.core;

import java.math.BigDecimal;

public record TradeProduct(long productId, String title, String description,
                           BigDecimal price, int availableQuantity) {
}
