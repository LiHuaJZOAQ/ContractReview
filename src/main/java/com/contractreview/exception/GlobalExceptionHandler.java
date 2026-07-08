package com.contractreview.exception;

import com.contractreview.common.BusinessException;
import com.contractreview.common.R;
import com.contractreview.domain.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusinessException(BusinessException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        R<Void> r = R.error(e.getCode(), e.getMessage());
        r.setRequestId(UUID.randomUUID().toString().replace("-", ""));
        HttpStatus status = e.getCode() == ErrorCode.RATE_LIMITED.getCode()
                ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(r);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        R<Void> r = R.error(400, message);
        r.setRequestId(UUID.randomUUID().toString().replace("-", ""));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(r);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleUnknown(Exception e) {
        log.error("Unexpected error", e);
        R<Void> r = R.error(500, "服务器内部错误");
        r.setRequestId(UUID.randomUUID().toString().replace("-", ""));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
    }
}
