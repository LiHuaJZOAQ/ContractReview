package com.contractreview.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;
    private final int maxRequestsPerMinute;

    public RateLimitFilter(RedisTemplate<String, Object> redisTemplate,
                           @Value("${contract.rate-limit.max-per-minute:30}") int maxRequestsPerMinute) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setLocation(new ClassPathResource("scripts/rate_limit.lua"));
        this.rateLimitScript.setResultType(Long.class);
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            String key = "rate:limit:" + userId;
            List<String> keys = Collections.singletonList(key);
            Long allowed = redisTemplate.execute(rateLimitScript, keys,
                    String.valueOf(60), String.valueOf(maxRequestsPerMinute), String.valueOf(System.currentTimeMillis()));

            if (allowed == null || allowed == 0) {
                response.setStatus(429);
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\":1008,\"message\":\"请求频率超限，请稍后再试\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/");
    }
}
