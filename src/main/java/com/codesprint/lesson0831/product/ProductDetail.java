package com.codesprint.lesson0831.product;

import java.math.BigDecimal;

public record ProductDetail(
        long id,
        String title,
        String description,
        BigDecimal price,
        boolean available
) {
}
