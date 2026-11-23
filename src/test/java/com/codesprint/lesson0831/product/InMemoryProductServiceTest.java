package com.codesprint.lesson0831.product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
class InMemoryProductServiceTest {
    @Test
    void shouldReturnProductDetailWhenIdExists(){
        InMemoryProductService service = new InMemoryProductService();
        ProductDetail detail = service.getDetail(1L);
        assertEquals(1L,detail.id());
        assertEquals("Java 入门书",detail.title());
        assertTrue(detail.available());
    }
    @Test
    void shouldRejectWhenProductDoesNotExist() {
        InMemoryProductService service = new InMemoryProductService();

        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> service.getDetail(999L)
        );

        assertEquals("商品不存在", exception.getMessage());
        assertEquals("NOT_FOUND", exception.getCode());
    }
    @Test
    void shouldRejectWhenProductIdIsNotPositive() {
        InMemoryProductService service = new InMemoryProductService();

        ProductValidationException exception = assertThrows(
                ProductValidationException.class,
                () -> service.getDetail(0L)
        );

        assertEquals("商品编号必须大于0", exception.getMessage());
        assertEquals("INVALID_ARGUMENT", exception.getCode());
    }
    @Test
    void shouldSearchProductsByKeyword() {
        InMemoryProductService service = new InMemoryProductService();

        List<ProductSummary> results = service.search(
                new ProductSearchRequest("Java", 1, 10, "title_asc")
        );

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).id());
        assertEquals("Java 入门书", results.get(0).title());
        assertEquals(0, results.get(0).price().compareTo(new java.math.BigDecimal("59.90")));
    }
    @Test
    void shouldRejectBlankKeyword() {
        InMemoryProductService service = new InMemoryProductService();

        ProductValidationException exception = assertThrows(
                ProductValidationException.class,
                () -> service.search(
                        new ProductSearchRequest("   ", 1, 10, "title_asc")
                )
        );

        assertEquals("关键词不能为空", exception.getMessage());
        assertEquals("INVALID_ARGUMENT", exception.getCode());
    }
    @Test
    void shouldRejectInvalidPage() {
        InMemoryProductService service = new InMemoryProductService();

        ProductValidationException exception = assertThrows(
                ProductValidationException.class,
                () -> service.search(
                        new ProductSearchRequest("Java", 0, 10, "title_asc")
                )
        );

        assertEquals("页码必须大于0", exception.getMessage());
        assertEquals("INVALID_ARGUMENT", exception.getCode());
    }
    @Test
    void shouldRejectInvalidSize() {
        InMemoryProductService service = new InMemoryProductService();

        ProductValidationException exception = assertThrows(
                ProductValidationException.class,
                () -> service.search(
                        new ProductSearchRequest("Java", 1, 0, "title_asc")
                )
        );

        assertEquals("每页数量必须大于0", exception.getMessage());
        assertEquals("INVALID_ARGUMENT", exception.getCode());
    }
    @Test
    void shouldRejectUnknownSort() {
        InMemoryProductService service = new InMemoryProductService();

        ProductValidationException exception = assertThrows(
                ProductValidationException.class,
                () -> service.search(
                        new ProductSearchRequest("Java", 1, 10, "unknown_sort")
                )
        );

        assertEquals("不支持的排序方式", exception.getMessage());
        assertEquals("INVALID_ARGUMENT", exception.getCode());
    }
    @Test
    void shouldSearchProductsIgnoringCase() {
        InMemoryProductService service = new InMemoryProductService();

        List<ProductSummary> results = service.search(
                new ProductSearchRequest("java", 1, 10, "title_asc")
        );

        assertEquals(1, results.size());
        assertEquals("Java 入门书", results.get(0).title());
    }
    @Test
    void shouldReturnEmptyWhenNoProductMatches() {
        InMemoryProductService service = new InMemoryProductService();

        List<ProductSummary> results = service.search(
                new ProductSearchRequest("Python", 1, 10, "title_asc")
        );

        assertTrue(results.isEmpty());
    }
    @Test
    void shouldReturnEmptyWhenPageAfterLast() {
        InMemoryProductService service = new InMemoryProductService();

        List<ProductSummary> results = service.search(
                new ProductSearchRequest("Java", 2, 1, "title_asc")
        );

        assertTrue(results.isEmpty());
    }
    @Test
    void shouldSortProductsByTitleAscending() {
        InMemoryProductService service = new InMemoryProductService();

        List<ProductSummary> results = service.search(
                new ProductSearchRequest("书", 1, 10, "title_asc")
        );

        assertEquals(3, results.size());
        assertEquals(1L, results.get(0).id());
        assertEquals(2L, results.get(1).id());
        assertEquals(3L, results.get(2).id());
    }
    @Test
    void shouldSortProductsByPriceDescending() {
        InMemoryProductService service = new InMemoryProductService();

        List<ProductSummary> results = service.search(
                new ProductSearchRequest("书", 1, 10, "price_desc")
        );

        assertEquals(3, results.size());
        assertEquals(2L, results.get(0).id());
        assertEquals(1L, results.get(1).id());
        assertEquals(3L, results.get(2).id());
    }
    @Test
    void shouldReturnProductDetailFromSharedProductList() {
        InMemoryProductService service = new InMemoryProductService();

        ProductDetail detail = service.getDetail(2L);

        assertEquals(2L, detail.id());
        assertEquals("Spring Boot 实战书", detail.title());
        assertTrue(detail.available());
    }
    @Test
    void sameSearchRequestShouldReuseCachedResult() {
        InMemoryProductService service =
                new InMemoryProductService();

        ProductSearchRequest request =
                new ProductSearchRequest(
                        "Java",
                        1,
                        10,
                        "title_asc"
                );

        List<ProductSummary> first =
                service.search(request);

        List<ProductSummary> second =
                service.search(request);

        assertEquals(first, second);
        assertTrue(first == second);
    }
}
