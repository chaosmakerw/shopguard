package com.codesprint.lesson0831.product;
import java.util.List;
public interface ProductService {
    List<ProductSummary> search(ProductSearchRequest request);
    ProductDetail getDetail(long productId);
}
