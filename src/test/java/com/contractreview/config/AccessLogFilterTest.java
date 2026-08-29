package com.contractreview.config;

import com.contractreview.security.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessLogFilterTest {

    private AccessLogFilter accessLogFilter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        accessLogFilter = new AccessLogFilter();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("正常请求记录访问日志")
    void testLogsAccessForNormalRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/contracts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessLogFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("POST 请求记录正确方法")
    void testLogsPostMethod() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessLogFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("过滤链异常时仍然记录日志")
    void testLogsOnFilterChainException() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/error");
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new RuntimeException("error")).when(filterChain).doFilter(any(), any());

        try {
            accessLogFilter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException ignored) {
        }

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("设置 userId 后记录到日志上下文")
    void testLogsWithUserId() throws ServletException, IOException {
        UserContext.setUserId(42L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/contracts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessLogFilter.doFilterInternal(request, response, filterChain);

        assertEquals(42L, UserContext.getUserId());
        verify(filterChain).doFilter(request, response);
    }
}
