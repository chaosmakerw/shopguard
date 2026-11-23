package com.codesprint.lesson0831.platform;

import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(basePackages="com.codesprint.lesson0831.platform")
@Order(-10)
@Profile("platform")
public class PlatformExceptionHandler {
    @ExceptionHandler(PlatformException.class) public ResponseEntity<?> platform(PlatformException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of("code",exception.code(),"message",exception.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,IllegalArgumentException.class,ArithmeticException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class}) public ResponseEntity<?> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("code","INVALID_INPUT","message","输入格式不正确，请检查长度与必填项"));
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class) public ResponseEntity<?> oversized(){
        return ResponseEntity.status(413).body(Map.of("code","FILE_TOO_LARGE","message","文档不能超过 256KB"));
    }
    @ExceptionHandler(Exception.class) public ResponseEntity<?> unavailable(Exception exception) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("platform_operation_failed type={}",exception.getClass().getSimpleName());
        return ResponseEntity.status(503).body(Map.of("code","SERVICE_UNAVAILABLE","message","服务暂时不可用，请检查配置或运行记录后重试"));
    }
}
