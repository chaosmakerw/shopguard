package com.codesprint.lesson0831.product;

import com.codesprint.lesson0831.common.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductTools {

    private final ProductService productService;

    public ProductTools(
            ProductService productService
    ) {
        this.productService = productService;
    }

    public ToolResult<List<ProductSummary>> searchProducts(
            ProductSearchRequest request
    ) {
        try {
            return ToolResult.success(
                    productService.search(request)
            );
        } catch (BizException exception) {
            return ToolResult.failure(
                    exception.getCode(),
                    exception.getMessage()
            );
        }
    }

    public ToolResult<ProductDetail> getProductDetail(
            long productId
    ) {
        try {
            return ToolResult.success(
                    productService.getDetail(productId)
            );
        } catch (BizException exception) {
            return ToolResult.failure(
                    exception.getCode(),
                    exception.getMessage()
            );
        }
    }
}