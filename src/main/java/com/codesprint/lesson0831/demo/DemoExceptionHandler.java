package com.codesprint.lesson0831.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

@Profile("dev & demo & !prod")
@RestControllerAdvice(assignableTypes = {DemoController.class, DemoWorkbenchController.class})
public class DemoExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> malformedRequest(HttpServletRequest request) {
        return Map.of("status", 400, "message", request.getRequestURI().contains("/workbench/")
                ? "请求格式不合法，请使用当前接口约定的字段"
                : "请求格式不合法，仅接受 question 字段");
    }
}
