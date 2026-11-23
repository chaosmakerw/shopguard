package com.codesprint.lesson0831.trade.api;

import com.codesprint.lesson0831.trade.core.TradeException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = TradeController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TradeExceptionHandler {
    public record Error(String code, String message) { }
    @ExceptionHandler(TradeException.class)
    public ResponseEntity<Error> trade(TradeException exception) {
        return ResponseEntity.status(exception.httpStatus()).body(new Error(exception.code(), exception.getMessage()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Error> malformed(Exception exception) {
        return ResponseEntity.badRequest().body(new Error("INVALID_INPUT", "请求格式不正确，请检查编号和数量"));
    }
}
