package com.contractreview.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MdcFilterTest {

    private MdcFilter mdcFilter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        mdcFilter = new MdcFilter();
    }

    @Test
    @DisplayName("无 X-Request-Id 头时生成 UUID 作为 traceId")
    void testGeneratesTraceIdWhenNoneProvided() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedTraceId = new AtomicReference<>();

        doAnswer(inv -> {
            capturedTraceId.set(MDC.get(MdcFilter.TRACE_ID));
            return null;
        }).when(filterChain).doFilter(any(), any());

        mdcFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(capturedTraceId.get());
        assertEquals(32, capturedTraceId.get().length());
        assertEquals(capturedTraceId.get(), response.getHeader("X-Request-Id"));
    }

    @Test
    @DisplayName("有 X-Request-Id 头时使用传入的值")
    void testUsesProvidedTraceId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "custom-trace-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedTraceId = new AtomicReference<>();

        doAnswer(inv -> {
            capturedTraceId.set(MDC.get(MdcFilter.TRACE_ID));
            return null;
        }).when(filterChain).doFilter(any(), any());

        mdcFilter.doFilterInternal(request, response, filterChain);

        assertEquals("custom-trace-id-123", capturedTraceId.get());
        assertEquals("custom-trace-id-123", response.getHeader("X-Request-Id"));
    }

    @Test
    @DisplayName("空字符串 X-Request-Id 时生成新 traceId")
    void testBlankTraceIdGeneratesNew() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedTraceId = new AtomicReference<>();

        doAnswer(inv -> {
            capturedTraceId.set(MDC.get(MdcFilter.TRACE_ID));
            return null;
        }).when(filterChain).doFilter(any(), any());

        mdcFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(capturedTraceId.get());
        assertFalse(capturedTraceId.get().isBlank());
    }

    @Test
    @DisplayName("过滤链执行完成后清理 MDC")
    void testMdcCleanedUpAfterFilter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        mdcFilter.doFilterInternal(request, response, filterChain);

        assertNull(MDC.get(MdcFilter.TRACE_ID));
        assertNull(MDC.get(MdcFilter.USER_ID));
    }

    @Test
    @DisplayName("过滤链异常时也清理 MDC")
    void testMdcCleanedUpOnException() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new RuntimeException("test error")).when(filterChain).doFilter(any(), any());

        try {
            mdcFilter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException ignored) {
        }

        assertNull(MDC.get(MdcFilter.TRACE_ID));
        assertNull(MDC.get(MdcFilter.USER_ID));
    }

    @Test
    @DisplayName("调用 doFilter 后委托给 filterChain")
    void testDelegatesToFilterChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        mdcFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
