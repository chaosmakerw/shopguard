package com.codesprint.lesson0831.common;

public class BizException extends RuntimeException {

    private final String code;

    public BizException(
            String code,
            String message
    ) {
        super(message);

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                    "业务错误码不能为空"
            );
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "业务错误消息不能为空"
            );
        }

        this.code = code;
    }

    public String getCode() {
        return code;
    }
}