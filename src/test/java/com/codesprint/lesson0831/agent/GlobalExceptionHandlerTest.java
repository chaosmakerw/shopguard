package com.codesprint.lesson0831.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void illegal_argument_should_return_400() {
        GlobalExceptionHandler.ErrorResponse response =
                handler.handleIllegalArgument(
                        new IllegalArgumentException("问题不能为空")
                );

        assertEquals(400, response.status());
        assertEquals("问题不能为空", response.message());
    }

    @Test
    void model_failure_should_return_502() {
        GlobalExceptionHandler.ErrorResponse response =
                handler.handleModelCall(
                        new ModelCallException("模型调用失败")
                );

        assertEquals(502, response.status());
        assertEquals("模型服务暂时不可用", response.message());
    }

    @Test
    void model_failure_should_not_expose_internal_message() {
        GlobalExceptionHandler.ErrorResponse response =
                handler.handleModelCall(
                        new ModelCallException(
                                "connection refused at secret-internal-host"
                        )
                );

        assertEquals(502, response.status());
        assertEquals("模型服务暂时不可用", response.message());
    }
}