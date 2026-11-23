package com.codesprint.lesson0831.product;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("!prod & !trade-demo")
public class InMemoryProductService implements ProductService {
    private final List<ProductDetail> products = List.of(
            new ProductDetail(
                    1L,
                    "Java 入门书",
                    "适合零基础学习 Java",
                    new BigDecimal("59.90"),
                    true
            ),
            new ProductDetail(
                    2L,
                    "Spring Boot 实战书",
                    "学习 Spring Boot Web 开发",
                    new BigDecimal("89.90"),
                    true
            ),
            new ProductDetail(
                    3L,
                    "数据库基础书",
                    "学习关系型数据库基础",
                    new BigDecimal("39.90"),
                    true
            )
    );
    private final Map<
                ProductSearchRequest,
                List<ProductSummary>
                > searchCache = new ConcurrentHashMap<>();
    @Override
    public List<ProductSummary> search(ProductSearchRequest request) {
        if (request == null) {
            throw new ProductValidationException("搜索请求不能为空");
        }

        if (request.keyword() == null || request.keyword().isBlank()) {
            throw new ProductValidationException("关键词不能为空");
        }
        if (request.size() <= 0) {
            throw new ProductValidationException("每页数量必须大于0");
        }

        if (!Set.of(
                "title_asc",
                "title_desc",
                "price_asc",
                "price_desc"
        ).contains(request.sort())) {
            throw new ProductValidationException("不支持的排序方式");
        }
        if (request.page() <= 0) {
            throw new ProductValidationException("页码必须大于0");
        }
        String keyword = request.keyword()
                .trim()
                .toLowerCase(Locale.ROOT);

        Comparator<ProductSummary> comparator = switch (request.sort()) {
            case "title_asc" -> Comparator.comparing(ProductSummary::title);
            case "title_desc" -> Comparator.comparing(ProductSummary::title).reversed();
            case "price_asc" -> Comparator.comparing(ProductSummary::price);
            case "price_desc" -> Comparator.comparing(ProductSummary::price).reversed();
            default -> throw new ProductValidationException("不支持的排序方式");
        };

        long skip = (long) (request.page() - 1) * request.size();

        return searchCache.computeIfAbsent(
                request,
                this::executeSearch
        );
    }
    private List<ProductSummary> executeSearch(
            ProductSearchRequest request
    ) {
        String keyword = request.keyword()
                .trim()
                .toLowerCase(Locale.ROOT);

        Comparator<ProductSummary> comparator = switch (request.sort()) {
            case "title_asc" ->
                    Comparator.comparing(ProductSummary::title);
            case "title_desc" ->
                    Comparator.comparing(ProductSummary::title).reversed();
            case "price_asc" ->
                    Comparator.comparing(ProductSummary::price);
            case "price_desc" ->
                    Comparator.comparing(ProductSummary::price).reversed();
            default ->
                    throw new IllegalArgumentException("不支持的排序方式");
        };

        long skip = (long) (request.page() - 1)
                * request.size();

        return products.stream()
                .filter(product -> product.title()
                        .toLowerCase(Locale.ROOT)
                        .contains(keyword))
                .map(product -> new ProductSummary(
                        product.id(),
                        product.title(),
                        product.price()
                ))
                .sorted(comparator)
                .skip(skip)
                .limit(request.size())
                .toList();
    }
    @Override
    public ProductDetail getDetail(long productId) {

        if (productId <= 0) {
            throw new ProductValidationException("商品编号必须大于0");
        }
        return products.stream()
                .filter(product -> product.id() == productId)
                .findFirst()
                .orElseThrow(() -> new ProductNotFoundException("商品不存在"));
    }
}
