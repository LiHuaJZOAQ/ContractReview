package com.contractreview.service.impl;

import com.contractreview.service.SseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseServiceImpl implements SseService {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    private static final long SSE_TIMEOUT_MS = 300_000L;

    @Override
    public SseEmitter createEmitter(Long taskId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.put(taskId, emitter);
        emitter.onCompletion(() -> {
            emitters.remove(taskId);
            log.debug("SSE emitter completed for task {}", taskId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(taskId);
            log.debug("SSE emitter timed out for task {}", taskId);
        });
        emitter.onError(e -> {
            emitters.remove(taskId);
            log.debug("SSE emitter error for task {}: {}", taskId, e.getMessage());
        });
        return emitter;
    }

    @Override
    public void sendProgress(Long taskId, String status, int progress, String message) {
        send(taskId, "progress", Map.of("status", status, "progress", progress, "message", message));
    }

    @Override
    public void sendLlmOutput(Long taskId, String agent, String content) {
        send(taskId, "llm_output", Map.of("agent", agent, "content", content, "timestamp", System.currentTimeMillis()));
    }

    @Override
    public void sendComplete(Long taskId, String reportId) {
        send(taskId, "complete", Map.of("status", "completed", "progress", 100, "message", "审查完成", "reportId", reportId));
    }

    @Override
    public void sendError(Long taskId, String message) {
        send(taskId, "error", Map.of("status", "failed", "progress", -1, "message", message));
    }

    @Override
    public void removeEmitter(Long taskId) {
        SseEmitter emitter = emitters.remove(taskId);
        if (emitter != null) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("Failed to complete SSE emitter for task {}: {}", taskId, e.getMessage());
        }
        }
    }

    private void send(Long taskId, String event, Map<String, Object> data) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            emitters.remove(taskId);
            log.warn("SSE send failed for task {}: {}", taskId, e.getMessage());
        }
    }
}
