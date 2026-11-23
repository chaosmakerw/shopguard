package com.codesprint.lesson0831.order;

import com.codesprint.lesson0831.product.ToolArgumentValidator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderToolArgumentValidatorTest {

    private final ToolArgumentValidator validator =
            new ToolArgumentValidator();

    @Test
    void shouldAcceptEmptyArguments() {
        var result = validator.validate(
                "list_my_orders",
                Map.of()
        );

        assertTrue(result.success());
        assertEquals("OK", result.code());
    }

    @Test
    void shouldRejectUserId() {
        var result = validator.validate(
                "list_my_orders",
                Map.of("userId", 1002L)
        );

        assertFalse(result.success());
        assertEquals("UNKNOWN_ARGUMENT", result.code());
    }

    @Test
    void shouldRejectBuyerId() {
        var result = validator.validate(
                "list_my_orders",
                Map.of("buyerId", 1002L)
        );

        assertFalse(result.success());
        assertEquals("UNKNOWN_ARGUMENT", result.code());
    }

    @Test
    void shouldRejectSellerId() {
        var result = validator.validate(
                "list_my_orders",
                Map.of("sellerId", 1002L)
        );

        assertFalse(result.success());
        assertEquals("UNKNOWN_ARGUMENT", result.code());
    }

    @Test
    void shouldRejectNullArguments() {
        var result = validator.validate(
                "list_my_orders",
                null
        );

        assertFalse(result.success());
        assertEquals("MISSING_ARGUMENT", result.code());
    }
}