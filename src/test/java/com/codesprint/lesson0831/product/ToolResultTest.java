package com.codesprint.lesson0831.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolResultTest {

    @Test
    void successShouldContainData() {
        List<ProductSummary> expected = List.of(
                new ProductSummary(
                        1L,
                        "Java 入门书",
                        new BigDecimal("59.90")
                )
        );

        ToolResult<List<ProductSummary>> result =
                ToolResult.success(expected);

        assertTrue(result.success());
        assertEquals("OK", result.code());
        assertEquals("", result.message());
        assertEquals(expected, result.data());
    }

    @Test
    void failureShouldContainErrorWithoutData() {
        ToolResult<Void> result = ToolResult.failure(
                "INVALID_ARGUMENT",
                "关键词不能为空"
        );

        assertFalse(result.success());
        assertEquals("INVALID_ARGUMENT", result.code());
        assertEquals("关键词不能为空", result.message());
        assertEquals(null, result.data());
    }
}