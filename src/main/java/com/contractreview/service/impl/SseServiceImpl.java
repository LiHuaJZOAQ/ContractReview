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

    @Override
    public SseEmitter createEmitter(Long taskId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(taskId, emitter);
        emitter.onCompletion(() -> emitters.remove(taskId));
        emitter.onTimeout(() -> emitters.remove(taskId));
        emitter.onError(e -> emitters.remove(taskId));
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
            } catch (Exception ignored) {}
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
