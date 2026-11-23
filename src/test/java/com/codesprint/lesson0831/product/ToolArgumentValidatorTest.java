package com.codesprint.lesson0831.product;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.math.BigDecimal;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;

class ToolArgumentValidatorTest {

    private final ToolArgumentValidator validator =
            new ToolArgumentValidator();

    @Test
    void shouldRejectUnknownTool() {
        ToolResult<Void> result = validator.validate(
                "delete_products",
                Map.of()
        );

        assertFalse(result.success());
        assertEquals("UNKNOWN_TOOL", result.code());
    }

    @Test
    void shouldRejectUnknownArgument() {
        ToolResult<Void> result = validator.validate(
                "search_products",
                Map.of(
                        "keyword", "Java",
                        "page", 1,
                        "size", 10,
                        "sort", "title_asc",
                        "userId", "other-user"
                )
        );

        assertFalse(result.success());
        assertEquals("UNKNOWN_ARGUMENT", result.code());
    }

    @Test
    void shouldRejectEmptyArguments() {
        ToolResult<Void> result = validator.validate(
                "search_products",
                Map.of()
        );

        assertFalse(result.success());
        assertEquals("MISSING_ARGUMENT", result.code());
    }

    @Test
    void shouldRejectWrongArgumentType() {
        ToolResult<Void> result = validator.validate(
                "search_products",
                Map.of(
                        "keyword", "Java",
                        "page", "第一页",
                        "size", 10,
                        "sort", "title_asc"
                )
        );

        assertFalse(result.success());
        assertEquals("INVALID_ARGUMENT", result.code());
    }
    @Test
    void shouldRejectMissingSearchField() {
        ToolResult<Void> result = validator.validate(
                "search_products",
                Map.of(
                        "keyword", "Java",
                        "page", 1,
                        "size", 10
                )
        );

        assertFalse(result.success());
        assertEquals("MISSING_ARGUMENT", result.code());
        assertEquals("缺少参数: sort", result.message());
    }
    @Test
    void validDetailArgumentsShouldPass() {
        ToolResult<Void> result = validator.validate(
                "get_product_detail",
                Map.of("productId", 1)
        );

        assertTrue(result.success());
        assertEquals("OK", result.code());
    }
    @Test
    void shouldRejectWrongDetailArgumentType() {
        ToolResult<Void> result = validator.validate(
                "get_product_detail",
                Map.of("productId", "1")
        );

        assertFalse(result.success());
        assertEquals("INVALID_ARGUMENT", result.code());
        assertEquals(
                "参数 productId 必须是整数",
                result.message()
        );
    }
    @Test
    void shouldRejectDecimalPage() {
        ToolResult<Void> result = validator.validate(
                "search_products",
                Map.of(
                        "keyword", "Java",
                        "page", 1.5,
                        "size", 10,
                        "sort", "title_asc"
                )
        );

        assertFalse(result.success());
        assertEquals("INVALID_ARGUMENT", result.code());
        assertEquals("参数 page 必须是整数", result.message());
    }
    @Test
    void shouldRejectNullKeyword() {
        Map<String, Object> arguments = new java.util.HashMap<>();
        arguments.put("keyword", null);
        arguments.put("page", 1);
        arguments.put("size", 10);
        arguments.put("sort", "title_asc");

        ToolResult<Void> result = validator.validate(
                "search_products",
                arguments
        );

        assertFalse(result.success());
        assertEquals("INVALID_ARGUMENT", result.code());
        assertEquals("参数 keyword 必须是字符串", result.message());
    }
    @Test
    void shouldRejectNonPositiveProductId() {
        ToolResult<Void> result = validator.validate(
                "get_product_detail",
                Map.of("productId", 0)
        );

        assertFalse(result.success());
        assertEquals("INVALID_ARGUMENT", result.code());
        assertEquals("参数 productId 必须大于0", result.message());
    }
    @Test
    void shouldRejectFractionThatDoubleWouldRoundAway() {
        ToolResult<Void> result = validator.validate(
                "get_product_detail",
                Map.of(
                        "productId",
                        new BigDecimal("1.0000000000000000001")
                )
        );

        assertFalse(result.success());
        assertEquals("INVALID_ARGUMENT", result.code());
    }
    @Test
    void shouldAcceptLongMaximumAndRejectLargerValue() {
        BigInteger maximum = BigInteger.valueOf(Long.MAX_VALUE);

        assertTrue(validator.validate(
                "get_product_detail",
                Map.of("productId", maximum)
        ).success());

        ToolResult<Void> rejected = validator.validate(
                "get_product_detail",
                Map.of("productId", maximum.add(BigInteger.ONE))
        );

        assertFalse(rejected.success());
        assertEquals("INVALID_ARGUMENT", rejected.code());
    }
    @Test
    void shouldRejectPageOutsideIntRange() {
        ToolResult<Void> rejected = validator.validate(
                "search_products",
                Map.of(
                        "keyword", "Java",
                        "page", 2147483648L,
                        "size", 10,
                        "sort", "title_asc"
                )
        );

        assertFalse(rejected.success());
        assertEquals("INVALID_ARGUMENT", rejected.code());

        ToolResult<Void> accepted = validator.validate(
                "search_products",
                Map.of(
                        "keyword", "Java",
                        "page", 1,
                        "size", 10,
                        "sort", "title_asc"
                )
        );

        assertTrue(accepted.success());
        assertEquals("OK", accepted.code());
    }
}