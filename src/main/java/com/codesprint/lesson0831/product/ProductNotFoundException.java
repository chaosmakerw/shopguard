package com.codesprint.lesson0831.product;

import com.codesprint.lesson0831.common.BizException;

public class ProductNotFoundException
        extends BizException {

    public ProductNotFoundException(
            String message
    ) {
        super(
                "NOT_FOUND",
                message
        );
    }
}