package com.codesprint.lesson0831.product;

import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class JdbcProductService
        implements ProductService {

    private static final Set<String> ALLOWED_SORTS =
            Set.of(
                    "title_asc",
                    "title_desc",
                    "price_asc",
                    "price_desc"
            );

    private final DataSource dataSource;
    private final RedisProductDetailCache detailCache;

    public JdbcProductService(
            DataSource dataSource
    ) {
        this(dataSource, null);
    }

    public JdbcProductService(DataSource dataSource, RedisProductDetailCache detailCache) {
        this.detailCache = detailCache;
        this.dataSource =
                Objects.requireNonNull(
                        dataSource,
                        "DataSource 不能为空"
                );
    }

    @Override
    public List<ProductSummary> search(
            ProductSearchRequest request
    ) {
        validateSearchRequest(request);

        String orderBy =
                toOrderBy(request.sort());

        String sql = """
        SELECT id, title, price
        FROM products
        WHERE available = TRUE
          AND LOWER(title) LIKE ?
        ORDER BY %s
        LIMIT ? OFFSET ?
        """.formatted(orderBy);

        String keywordPattern =
                "%"
                        + request.keyword()
                        .trim()
                        .toLowerCase(Locale.ROOT)
                        + "%";

        long offset =
                (long) (request.page() - 1)
                        * request.size();

        List<ProductSummary> products =
                new ArrayList<>();

        try (
                Connection connection =
                        dataSource.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(
                    1,
                    keywordPattern
            );

            statement.setInt(
                    2,
                    request.size()
            );

            statement.setLong(
                    3,
                    offset
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                while (resultSet.next()) {
                    long id =
                            resultSet.getLong("id");

                    String title =
                            resultSet.getString("title");

                    BigDecimal price =
                            resultSet.getBigDecimal("price");

                    products.add(
                            new ProductSummary(
                                    id,
                                    title,
                                    price
                            )
                    );
                }
            }

            return List.copyOf(products);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "数据库查询失败",
                    exception
            );
        }
    }

    @Override
    public ProductDetail getDetail(
            long productId
    ) {
        if (productId <= 0) {
            throw new ProductValidationException(
                    "商品编号必须大于0"
            );
        }

        return detailCache == null ? loadDetail(productId) : detailCache.get(productId, this::loadDetail);
    }

    private ProductDetail loadDetail(long productId) {
        String sql = """
        SELECT
            id,
            title,
            description,
            price,
            available
        FROM products
        WHERE id = ?
          AND available = TRUE
        """;

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(
                    1,
                    productId
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                if (!resultSet.next()) {
                    throw new ProductNotFoundException(
                            "商品不存在"
                    );
                }

                return new ProductDetail(
                        resultSet.getLong("id"),
                        resultSet.getString("title"),
                        resultSet.getString(
                                "description"
                        ),
                        resultSet.getBigDecimal("price"),
                        resultSet.getBoolean(
                                "available"
                        )
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "数据库查询失败",
                    exception
            );
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private void validateSearchRequest(
            ProductSearchRequest request
    ) {
        if (request == null) {
            throw new ProductValidationException(
                    "搜索请求不能为空"
            );
        }

        if (request.keyword() == null
                || request.keyword().isBlank()) {
            throw new ProductValidationException(
                    "关键词不能为空"
            );
        }

        if (request.page() <= 0) {
            throw new ProductValidationException(
                    "页码必须大于0"
            );
        }

        if (request.size() <= 0) {
            throw new ProductValidationException(
                    "每页数量必须大于0"
            );
        }

        if (!ALLOWED_SORTS.contains(
                request.sort()
        )) {
            throw new ProductValidationException(
                    "不支持的排序方式"
            );
        }
    }

    private String toOrderBy(
            String sort
    ) {
        return switch (sort) {
            case "title_asc" ->
                    "title ASC";

            case "title_desc" ->
                    "title DESC";

            case "price_asc" ->
                    "price ASC";

            case "price_desc" ->
                    "price DESC";

            default ->
                    throw new ProductValidationException(
                            "不支持的排序方式"
                    );
        };
    }
}
