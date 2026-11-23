package com.codesprint.lesson0831.product;

import com.codesprint.lesson0831.common.BizException;

public class ProductValidationException
        extends BizException {

    public ProductValidationException(
            String message
    ) {
        super(
                "INVALID_ARGUMENT",
                message
        );
    }
}