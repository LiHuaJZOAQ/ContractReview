package com.contractreview.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    @DisplayName("TASK_NOT_FOUND 返回 HTTP 404")
    void testTaskNotFoundHttp404() {
        assertEquals(404, ErrorCode.TASK_NOT_FOUND.getHttpStatus());
    }

    @Test
    @DisplayName("QUOTA_INSUFFICIENT 返回 HTTP 429")
    void testQuotaInsufficientHttp429() {
        assertEquals(429, ErrorCode.QUOTA_INSUFFICIENT.getHttpStatus());
    }

    @Test
    @DisplayName("LLM_API_FAILED 返回 HTTP 502")
    void testLlmApiFailedHttp502() {
        assertEquals(502, ErrorCode.LLM_API_FAILED.getHttpStatus());
    }

    @Test
    @DisplayName("FORMAT_NOT_SUPPORTED 返回 HTTP 400")
    void testFormatNotSupportedHttp400() {
        assertEquals(400, ErrorCode.FORMAT_NOT_SUPPORTED.getHttpStatus());
    }

    @Test
    @DisplayName("INTERNAL_ERROR 返回 HTTP 500")
    void testInternalErrorHttp500() {
        assertEquals(500, ErrorCode.INTERNAL_ERROR.getHttpStatus());
    }

    @Test
    @DisplayName("所有 ErrorCode 有正确 code 和 message")
    void testAllErrorCodesHaveCodeAndMessage() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertTrue(ec.getCode() > 0, ec.name() + " code must be > 0");
            assertNotNull(ec.getMessage(), ec.name() + " message must not be null");
            assertFalse(ec.getMessage().isBlank(), ec.name() + " message must not be blank");
        }
    }
}
