package com.contractreview.config;

import com.contractreview.security.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final String ACCESS_LOG = "ACCESS_LOG";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = response.getStatus();
            Long userId = UserContext.getUserId();
            if (log.isDebugEnabled() || !isStreamingPath(uri)) {
                log.info("ACCESS {} {} {} {}ms userId={}", method, uri, status, duration, userId);
            } else {
                log.debug("ACCESS {} {} {} {}ms userId={}", method, uri, status, duration, userId);
            }
        }
    }

    private boolean isStreamingPath(String uri) {
        return uri != null && uri.matches("^/api/v1/contract/\\d+/progress$");
    }
}
