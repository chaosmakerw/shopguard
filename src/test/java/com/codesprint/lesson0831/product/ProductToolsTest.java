package com.codesprint.lesson0831.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ProductToolsTest {

    @Test
    void searchProductsShouldDelegateToProductService() {
        ProductService productService =
                mock(ProductService.class);

        ProductTools tools =
                new ProductTools(productService);

        ProductSearchRequest request =
                new ProductSearchRequest(
                        "Java",
                        1,
                        10,
                        "title_asc"
                );

        List<ProductSummary> expected = List.of(
                new ProductSummary(
                        1L,
                        "Java 入门书",
                        new BigDecimal("59.90")
                )
        );

        when(productService.search(request))
                .thenReturn(expected);

        ToolResult<List<ProductSummary>> actual =
                tools.searchProducts(request);

        assertTrue(actual.success());
        assertEquals("OK", actual.code());
        assertEquals(expected, actual.data());

        verify(productService).search(request);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void getProductDetailShouldDelegateToProductService() {
        ProductService productService =
                mock(ProductService.class);

        ProductTools tools =
                new ProductTools(productService);

        ProductDetail expected = new ProductDetail(
                2L,
                "Spring Boot 实战书",
                "学习 Spring Boot Web 开发",
                new BigDecimal("89.90"),
                true
        );

        when(productService.getDetail(2L))
                .thenReturn(expected);

        ToolResult<ProductDetail> actual =
                tools.getProductDetail(2L);

        assertTrue(actual.success());
        assertEquals("OK", actual.code());
        assertEquals(expected, actual.data());

        verify(productService).getDetail(2L);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void searchProductsShouldPreserveBusinessCode() {
        ProductService productService =
                mock(ProductService.class);

        ProductTools tools =
                new ProductTools(productService);

        ProductSearchRequest request =
                new ProductSearchRequest(
                        "   ",
                        1,
                        10,
                        "title_asc"
                );

        when(productService.search(request))
                .thenThrow(
                        new ProductValidationException(
                                "关键词不能为空"
                        )
                );

        ToolResult<List<ProductSummary>> result =
                tools.searchProducts(request);

        assertFalse(result.success());
        assertEquals(
                "INVALID_ARGUMENT",
                result.code()
        );
        assertEquals(
                "关键词不能为空",
                result.message()
        );
        assertNull(result.data());

        verify(productService).search(request);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void getProductDetailShouldPreserveBusinessCode() {
        ProductService productService =
                mock(ProductService.class);

        ProductTools tools =
                new ProductTools(productService);

        when(productService.getDetail(999L))
                .thenThrow(
                        new ProductNotFoundException(
                                "商品不存在"
                        )
                );

        ToolResult<ProductDetail> result =
                tools.getProductDetail(999L);

        assertFalse(result.success());
        assertEquals("NOT_FOUND", result.code());
        assertEquals(
                "商品不存在",
                result.message()
        );
        assertNull(result.data());

        verify(productService).getDetail(999L);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void technicalExceptionShouldContinueToDispatcher() {
        ProductService productService =
                mock(ProductService.class);

        ProductTools tools =
                new ProductTools(productService);

        when(productService.getDetail(2L))
                .thenThrow(
                        new IllegalStateException(
                                "数据库地址和内部异常"
                        )
                );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> tools.getProductDetail(2L)
                );

        assertEquals(
                "数据库地址和内部异常",
                exception.getMessage()
        );

        verify(productService).getDetail(2L);
        verifyNoMoreInteractions(productService);
    }
}