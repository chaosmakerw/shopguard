package com.codesprint.lesson0831.product;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolArgumentConverterTest {

    private final ToolArgumentConverter converter =
            new ToolArgumentConverter();

    @Test
    void shouldConvertSearchArgumentsToRequest() {
        ProductSearchRequest request =
                converter.toSearchRequest(
                        Map.of(
                                "keyword", "Java",
                                "page", 2,
                                "size", 5,
                                "sort", "price_desc"
                        )
                );

        assertEquals(
                new ProductSearchRequest(
                        "Java",
                        2,
                        5,
                        "price_desc"
                ),
                request
        );
    }

    @Test
    void shouldConvertProductIdToLong() {
        long productId = converter.toProductId(
                Map.of("productId", 2)
        );

        assertEquals(2L, productId);
    }
    @Test
    void shouldConvertExactNumericTypes() {
        ProductSearchRequest request = converter.toSearchRequest(
                Map.of(
                        "keyword", "Java",
                        "page", new BigDecimal("2.0"),
                        "size", BigInteger.valueOf(5),
                        "sort", "price_desc"
                )
        );

        assertEquals(
                new ProductSearchRequest("Java", 2, 5, "price_desc"),
                request
        );

        assertEquals(
                Long.MAX_VALUE,
                converter.toProductId(
                        Map.of(
                                "productId",
                                BigInteger.valueOf(Long.MAX_VALUE)
                        )
                )
        );
    }
    @Test
    void shouldRejectFractionWithoutTruncating() {
        assertThrows(
                ArithmeticException.class,
                () -> converter.toProductId(
                        Map.of(
                                "productId",
                                new BigDecimal("1.0000000000000000001")
                        )
                )
        );
    }
    @Test
    void shouldRejectOverflowWithoutWrapping() {
        assertThrows(
                ArithmeticException.class,
                () -> converter.toSearchRequest(
                        Map.of(
                                "keyword", "Java",
                                "page", 2147483648L,
                                "size", 10,
                                "sort", "title_asc"
                        )
                )
        );

        assertThrows(
                ArithmeticException.class,
                () -> converter.toProductId(
                        Map.of(
                                "productId",
                                BigInteger.valueOf(Long.MAX_VALUE)
                                        .add(BigInteger.ONE)
                        )
                )
        );
    }
}