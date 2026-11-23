package com.codesprint.lesson0831.product;
import java.math.BigDecimal;
public record ProductSummary (
    long id,
    String title,
    BigDecimal price
){}
