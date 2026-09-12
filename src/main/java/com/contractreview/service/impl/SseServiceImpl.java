package com.contractreview.service.impl;

import com.contractreview.service.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseServiceImpl implements SseService {

    private final Map<Long, ResponseBodyEmitter> emitters = new ConcurrentHashMap<>();

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ResponseBodyEmitter createEmitter(Long taskId) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(SSE_TIMEOUT_MS);
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
        send(taskId, "progress", ordered("status", status, "progress", progress, "message", message));
    }

    @Override
    public void sendLlmOutput(Long taskId, String agent, String content) {
        send(taskId, "llm_output", ordered("agent", agent, "content", content, "timestamp", System.currentTimeMillis()));
    }

    @Override
    public void sendComplete(Long taskId, String reportId) {
        send(taskId, "complete", ordered("status", "completed", "progress", 100, "message", "审查完成", "reportId", reportId));
    }

    @Override
    public void sendError(Long taskId, String message) {
        send(taskId, "error", ordered("status", "failed", "progress", -1, "message", message));
    }

    @Override
    public void removeEmitter(Long taskId) {
        ResponseBodyEmitter emitter = emitters.remove(taskId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("Failed to complete SSE emitter for task {}: {}", taskId, e.getMessage());
            }
        }
    }

    private void send(Long taskId, String type, Map<String, Object> data) {
        ResponseBodyEmitter emitter = emitters.get(taskId);
        if (emitter == null) return;
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("taskId", taskId);
        envelope.putAll(data);
        try {
            String line = MAPPER.writeValueAsString(envelope) + "\n";
            emitter.send(line, MediaType.APPLICATION_NDJSON);
        } catch (IOException | IllegalStateException e) {
            emitters.remove(taskId);
            log.warn("SSE send failed for task {}: {}", taskId, e.getMessage());
        }
    }

    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
