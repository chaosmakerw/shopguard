package com.codesprint.lesson0831.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.codesprint.lesson0831.order.OrderTools;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ToolDispatcherTest {

    @Test
    void shouldDispatchSearchTool() {
        ToolArgumentValidator validator =
                mock(ToolArgumentValidator.class);
        ToolArgumentConverter converter =
                mock(ToolArgumentConverter.class);
        ProductTools productTools =
                mock(ProductTools.class);

        Map<String, Object> arguments = Map.of(
                "keyword", "Java",
                "page", 1,
                "size", 10,
                "sort", "title_asc"
        );

        ProductSearchRequest request =
                new ProductSearchRequest(
                        "Java",
                        1,
                        10,
                        "title_asc"
                );

        ToolResult<Void> valid =
                ToolResult.success(null);

        ToolResult<List<ProductSummary>> expected =
                ToolResult.success(
                        List.of(
                                new ProductSummary(
                                        1L,
                                        "Java 入门书",
                                        new BigDecimal("59.90")
                                )
                        )
                );

        when(validator.validate(
                "search_products",
                arguments
        )).thenReturn(valid);

        when(converter.toSearchRequest(arguments))
                .thenReturn(request);

        when(productTools.searchProducts(request))
                .thenReturn(expected);

        ToolDispatcher dispatcher = new ToolDispatcher(
                validator,
                converter,
                productTools,
                mock(OrderTools.class)
        );

        ToolResult<?> actual = dispatcher.dispatch(
                "search_products",
                arguments
        );

        assertEquals(expected, actual);
        verify(validator).validate(
                "search_products",
                arguments
        );
        verify(converter).toSearchRequest(arguments);
        verify(productTools).searchProducts(request);
    }

    @Test
    void shouldDispatchDetailTool() {
        ToolArgumentValidator validator =
                mock(ToolArgumentValidator.class);
        ToolArgumentConverter converter =
                mock(ToolArgumentConverter.class);
        ProductTools productTools =
                mock(ProductTools.class);

        Map<String, Object> arguments =
                Map.of("productId", 2);

        ToolResult<Void> valid =
                ToolResult.success(null);

        ToolResult<ProductDetail> expected =
                ToolResult.success(
                        new ProductDetail(
                                2L,
                                "Spring Boot 实战书",
                                "学习 Spring Boot Web 开发",
                                new BigDecimal("89.90"),
                                true
                        )
                );

        when(validator.validate(
                "get_product_detail",
                arguments
        )).thenReturn(valid);

        when(converter.toProductId(arguments))
                .thenReturn(2L);

        when(productTools.getProductDetail(2L))
                .thenReturn(expected);

        ToolDispatcher dispatcher = new ToolDispatcher(
                validator,
                converter,
                productTools,
                mock(OrderTools.class)
        );
        ToolResult<?> actual = dispatcher.dispatch(
                "get_product_detail",
                arguments
        );

        assertEquals(expected, actual);
        verify(validator).validate(
                "get_product_detail",
                arguments
        );
        verify(converter).toProductId(arguments);
        verify(productTools).getProductDetail(2L);
    }
    @Test
    void shouldStopWhenValidationFails() {
        ToolArgumentValidator validator =
                mock(ToolArgumentValidator.class);
        ToolArgumentConverter converter =
                mock(ToolArgumentConverter.class);
        ProductTools productTools =
                mock(ProductTools.class);

        Map<String, Object> arguments =
                Map.of("page", "错误类型");

        ToolResult<Void> invalid =
                ToolResult.failure(
                        "INVALID_ARGUMENT",
                        "参数 page 必须是整数"
                );

        when(validator.validate(
                "search_products",
                arguments
        )).thenReturn(invalid);

        ToolDispatcher dispatcher = new ToolDispatcher(
                validator,
                converter,
                productTools,
                mock(OrderTools.class)
        );
        ToolResult<?> actual = dispatcher.dispatch(
                "search_products",
                arguments
        );

        assertEquals(invalid, actual);

        verify(validator).validate(
                "search_products",
                arguments
        );

        verifyNoInteractions(converter, productTools);
    }
    @Test
    void shouldReturnInternalErrorWhenConversionFails() {
        ToolArgumentValidator validator = mock(ToolArgumentValidator.class);
        ToolArgumentConverter converter = mock(ToolArgumentConverter.class);
        ProductTools productTools = mock(ProductTools.class);

        Map<String, Object> arguments = Map.of("productId", 2);

        when(validator.validate("get_product_detail", arguments))
                .thenReturn(ToolResult.success(null));

        when(converter.toProductId(arguments))
                .thenThrow(new IllegalStateException("内部转换异常详情"));

        ToolDispatcher dispatcher =
                new ToolDispatcher(validator, converter, productTools,mock(OrderTools.class));

        ToolResult<?> actual =
                dispatcher.dispatch("get_product_detail", arguments);

        assertEquals(
                ToolResult.failure(
                        "INTERNAL_ERROR",
                        "工具执行失败，请稍后重试"
                ),
                actual
        );

        verify(validator).validate("get_product_detail", arguments);
        verify(converter).toProductId(arguments);
        verifyNoInteractions(productTools);
    }
    @Test
    void shouldReturnInternalErrorWhenToolExecutionFails() {
        ToolArgumentValidator validator = mock(ToolArgumentValidator.class);
        ToolArgumentConverter converter = mock(ToolArgumentConverter.class);
        ProductTools productTools = mock(ProductTools.class);

        Map<String, Object> arguments = Map.of("productId", 2);

        when(validator.validate("get_product_detail", arguments))
                .thenReturn(ToolResult.success(null));

        when(converter.toProductId(arguments)).thenReturn(2L);

        when(productTools.getProductDetail(2L))
                .thenThrow(new IllegalStateException("内部数据库连接详情"));

        ToolDispatcher dispatcher =
                new ToolDispatcher(validator, converter, productTools,mock(OrderTools.class));

        ToolResult<?> actual =
                dispatcher.dispatch("get_product_detail", arguments);

        assertEquals(
                ToolResult.failure(
                        "INTERNAL_ERROR",
                        "工具执行失败，请稍后重试"
                ),
                actual
        );

        verify(validator).validate("get_product_detail", arguments);
        verify(converter).toProductId(arguments);
        verify(productTools).getProductDetail(2L);
        verifyNoMoreInteractions(productTools);
    }
    @Test
    void shouldRunRealSearchChain() {
        ToolDispatcher dispatcher = new ToolDispatcher(
                new ToolArgumentValidator(),
                new ToolArgumentConverter(),
                new ProductTools(new InMemoryProductService()),
                mock(OrderTools.class)
        );

        ToolResult<?> result = dispatcher.dispatch(
                "search_products",
                Map.of(
                        "keyword", "Java",
                        "page", java.math.BigInteger.ONE,
                        "size", new BigDecimal("10.0"),
                        "sort", "title_asc"
                )
        );

        assertEquals(true, result.success());
        assertEquals("OK", result.code());

        @SuppressWarnings("unchecked")
        ToolResult<List<ProductSummary>> searchResult =
                (ToolResult<List<ProductSummary>>) result;

        assertEquals(1, searchResult.data().size());
        assertEquals(1L, searchResult.data().get(0).id());
        assertEquals(
                "Java 入门书",
                searchResult.data().get(0).title()
        );
    }
    @Test
    void shouldRejectOversizedPageBeforeCallingDownstream() {
        ToolArgumentConverter converter =
                mock(ToolArgumentConverter.class);
        ProductTools productTools = mock(ProductTools.class);
        OrderTools orderTools = mock(OrderTools.class);

        ToolDispatcher dispatcher = new ToolDispatcher(
                new ToolArgumentValidator(),
                converter,
                productTools,
                orderTools
        );

        ToolResult<?> result = dispatcher.dispatch(
                "search_products",
                Map.of(
                        "keyword", "Java",
                        "page", 2147483648L,
                        "size", 10,
                        "sort", "title_asc"
                )
        );

        assertEquals(false, result.success());
        assertEquals("INVALID_ARGUMENT", result.code());

        verifyNoInteractions(converter, productTools, orderTools);
    }
}