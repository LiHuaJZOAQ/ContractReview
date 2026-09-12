package com.contractreview.aop;

import com.contractreview.common.R;
import com.contractreview.domain.dto.AuthResponse;
import com.contractreview.domain.entity.OperationLog;
import com.contractreview.mapper.OperationLogMapper;
import com.contractreview.security.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditLog)")
    public Object logOperation(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long duration = System.currentTimeMillis() - start;

        try {
            Long userId = UserContext.getUserId();
            if (userId == null && result instanceof R<?> r && r.getData() instanceof AuthResponse authResp) {
                userId = authResp.getUserId();
            }
            if (userId == null) {
                return result;
            }
            OperationLog opLog = new OperationLog();
            opLog.setUserId(userId);
            opLog.setAction(auditLog.action());

            Object[] args = joinPoint.getArgs();
            for (Object arg : args) {
                if (arg instanceof Long) {
                    opLog.setTaskId((Long) arg);
                    break;
                }
            }

            String detail = objectMapper.writeValueAsString(Map.of(
                    "method", joinPoint.getSignature().getName(),
                    "duration", duration
            ));
            opLog.setDetail(detail);

            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                opLog.setIpAddress(getClientIp(request));
            }

            operationLogMapper.insert(opLog);
        } catch (Exception e) {
            log.warn("Failed to write operation log", e);
        }

        return result;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
