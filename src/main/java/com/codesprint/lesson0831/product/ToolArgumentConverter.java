package com.codesprint.lesson0831.product;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

public class ToolArgumentConverter {

    public ProductSearchRequest toSearchRequest(
            Map<String, Object> arguments
    ) {
        return new ProductSearchRequest(
                (String) arguments.get("keyword"),
                toIntegerExact(arguments.get("page")).intValueExact(),
                toIntegerExact(arguments.get("size")).intValueExact(),
                (String) arguments.get("sort")
        );
    }

    public long toProductId(Map<String, Object> arguments) {
        return toIntegerExact(
                arguments.get("productId")
        ).longValueExact();
    }

    public long toOrderId(Map<String, Object> arguments) {
        return Long.parseLong((String) arguments.get("orderId"));
    }

    private BigInteger toIntegerExact(Object value) {
        if (value instanceof BigInteger integer) {
            return integer;
        }

        if (value instanceof BigDecimal decimal) {
            return decimal.toBigIntegerExact();
        }

        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            return BigInteger.valueOf(((Number) value).longValue());
        }

        throw new IllegalArgumentException("参数必须是精确整数类型");
    }
}
