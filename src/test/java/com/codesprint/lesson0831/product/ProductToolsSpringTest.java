package com.codesprint.lesson0831.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ProductToolsSpringTest {

    @Autowired
    private ProductTools productTools;

    @Test
    void springShouldInjectProductServiceIntoTools() {
        ToolResult<List<ProductSummary>> result =
                productTools.searchProducts(
                        new ProductSearchRequest(
                                "Java",
                                1,
                                10,
                                "title_asc"
                        )
                );

        assertTrue(result.success());
        assertEquals("OK", result.code());
        assertEquals(1, result.data().size());
        assertEquals(1L, result.data().get(0).id());
        assertEquals(
                "Java 入门书",
                result.data().get(0).title()
        );
    }
}