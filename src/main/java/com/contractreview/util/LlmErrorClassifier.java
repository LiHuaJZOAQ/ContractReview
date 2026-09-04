package com.contractreview.util;

import com.contractreview.common.BusinessException;
import com.contractreview.domain.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class LlmErrorClassifier {

    private static final Pattern RESET_PATTERN = Pattern.compile("\"X-RateLimit-Reset\":(\\d+)");
    private static final Pattern REMAINING_PATTERN = Pattern.compile("\"X-RateLimit-Remaining\":\"(\\d+)\"");
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\"X-RateLimit-Limit\":\"(\\d+)\"");
    private static final Pattern SOURCE_PATTERN = Pattern.compile("\"limit_source\":\"([^\"]+)\"");

    public enum Kind {
        QUOTA_EXHAUSTED,
        RATE_LIMITED,
        OTHER
    }

    private LlmErrorClassifier() {
    }

    public static Kind classify(Throwable t) {
        if (!(t instanceof NonTransientAiException)) return Kind.OTHER;
        String msg = t.getMessage();
        if (msg == null || !msg.contains("429")) return Kind.OTHER;
        if (msg.contains("free-models-per-day") || msg.contains("quota")
                || msg.contains("daily") || msg.contains("per-day")) {
            return Kind.QUOTA_EXHAUSTED;
        }
        return Kind.RATE_LIMITED;
    }

    public static void rethrowAsBusiness(Throwable t) {
        Kind kind = classify(t);
        if (kind == Kind.OTHER) {
            if (t instanceof RuntimeException re) throw re;
            throw new RuntimeException(t);
        }

        String msg = t.getMessage();
        boolean isEmbedding = msg != null && msg.toLowerCase().contains("embedding");
        String modelType = isEmbedding ? "向量检索（Embedding）" : "对话/分类（LLM）";

        String limit = extract(msg, LIMIT_PATTERN);
        String remaining = extract(msg, REMAINING_PATTERN);
        String reset = extract(msg, RESET_PATTERN);
        String source = extract(msg, SOURCE_PATTERN);

        String detail;
        if (kind == Kind.QUOTA_EXHAUSTED) {
            String resetTime = formatReset(reset);
            detail = String.format(
                    "%s模型当日配额已用完（限额 %s 次，剩余 %s 次，重置时间 %s，限流来源 %s）",
                    modelType,
                    limit != null ? limit : "?",
                    remaining != null ? remaining : "0",
                    resetTime,
                    source != null ? source : "unknown");
            log.warn("LLM quota exhausted: type={}, limit={}, remaining={}, reset={}, source={}",
                    modelType, limit, remaining, reset, source);
        } else {
            detail = String.format("%s模型调用频率超限（限额 %s 次/单位时间，剩余 %s），请稍后重试",
                    modelType,
                    limit != null ? limit : "?",
                    remaining != null ? remaining : "0");
            log.warn("LLM rate limited: type={}, limit={}, remaining={}, reset={}",
                    modelType, limit, remaining, reset);
        }

        ErrorCode code = kind == Kind.QUOTA_EXHAUSTED
                ? ErrorCode.LLM_QUOTA_EXHAUSTED
                : ErrorCode.RATE_LIMITED;
        throw new BusinessException(code,
                code.getMessage() + " — " + detail);
    }

    private static String extract(String msg, Pattern p) {
        if (msg == null) return null;
        Matcher m = p.matcher(msg);
        return m.find() ? m.group(1) : null;
    }

    private static String formatReset(String epochMillis) {
        if (epochMillis == null) return "未知";
        try {
            long ms = Long.parseLong(epochMillis);
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date(ms));
        } catch (NumberFormatException e) {
            return epochMillis;
        }
    }
}