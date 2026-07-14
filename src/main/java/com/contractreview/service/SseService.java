package com.contractreview.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface SseService {
    SseEmitter createEmitter(Long taskId);
    void sendProgress(Long taskId, String status, int progress, String message);
    void sendLlmOutput(Long taskId, String agent, String content);
    void sendComplete(Long taskId, String reportId);
    void sendError(Long taskId, String message);
    void removeEmitter(Long taskId);
}
