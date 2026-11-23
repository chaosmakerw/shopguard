package com.codesprint.lesson0831.product;

public record ProductSearchRequest(
        String keyword,
        int page,
        int size,
        String sort
) {
}
