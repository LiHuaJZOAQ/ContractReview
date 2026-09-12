package com.contractreview.exception;

import com.contractreview.common.BusinessException;
import com.contractreview.common.R;
import com.contractreview.domain.enums.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("BusinessException 返回正确 HTTP 状态码 — TASK_NOT_FOUND → 404")
    void testBusinessExceptionReturnsCorrectHttpStatus() {
        BusinessException ex = new BusinessException(ErrorCode.TASK_NOT_FOUND);
        ResponseEntity<R<Void>> response = handler.handleBusinessException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(ErrorCode.TASK_NOT_FOUND.getCode(), response.getBody().getCode());
    }

    @Test
    @DisplayName("BusinessException QUOTA_INSUFFICIENT → 429")
    void testQuotaInsufficientReturns429() {
        BusinessException ex = new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
        ResponseEntity<R<Void>> response = handler.handleBusinessException(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    }

    @Test
    @DisplayName("BusinessException RATE_LIMITED → 429")
    void testRateLimitedReturns429() {
        BusinessException ex = new BusinessException(ErrorCode.RATE_LIMITED);
        ResponseEntity<R<Void>> response = handler.handleBusinessException(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    }

    @Test
    @DisplayName("BusinessException LLM_API_FAILED → 502")
    void testLlmApiFailedReturns502() {
        BusinessException ex = new BusinessException(ErrorCode.LLM_API_FAILED);
        ResponseEntity<R<Void>> response = handler.handleBusinessException(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    }

    @Test
    @DisplayName("BusinessException FORMAT_NOT_SUPPORTED → 400")
    void testFormatNotSupportedReturns400() {
        BusinessException ex = new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED);
        ResponseEntity<R<Void>> response = handler.handleBusinessException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("MethodArgumentNotValidException 返回 400 和字段错误信息")
    void testValidationException() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("request", "username", "不能为空");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
        ResponseEntity<R<Void>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("username"));
    }

    @Test
    @DisplayName("MaxUploadSizeExceededException 返回 FILE_SIZE_EXCEEDED")
    void testMaxUploadSizeException() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(20971520L);
        ResponseEntity<R<Void>> response = handler.handleMaxUploadSize(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCode.FILE_SIZE_EXCEEDED.getCode(), response.getBody().getCode());
    }

    @Test
    @DisplayName("未知异常返回 500")
    void testUnknownExceptionReturns500() {
        ResponseEntity<R<Void>> response = handler.handleUnknown(new RuntimeException("oops"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), response.getBody().getCode());
    }

    @Test
    @DisplayName("所有响应包含 requestId")
    void testAllResponsesContainRequestId() {
        BusinessException ex = new BusinessException(ErrorCode.TASK_NOT_FOUND);
        ResponseEntity<R<Void>> response = handler.handleBusinessException(ex);

        assertNotNull(response.getBody().getRequestId());
        assertFalse(response.getBody().getRequestId().isEmpty());
    }
}
