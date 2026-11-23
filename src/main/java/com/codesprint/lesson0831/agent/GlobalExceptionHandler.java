package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.common.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), exception.getMessage());
    }
    @ExceptionHandler(ModelCallException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handleModelCall(ModelCallException exception){
        return new ErrorResponse(HttpStatus.BAD_GATEWAY.value(), "模型服务暂时不可用");
    }

    @ExceptionHandler(BizException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleBizException(
            BizException exception
    ) {
        HttpStatus status = "UNAUTHORIZED".equals(exception.getCode())
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.BAD_REQUEST;
        return org.springframework.http.ResponseEntity
                .status(status)
                .body(new ErrorResponse(status.value(), exception.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不合法");

        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                message
        );
    }
    public record ErrorResponse(int status, String message) {
    }
}
