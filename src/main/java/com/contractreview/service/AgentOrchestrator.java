package com.contractreview.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface AgentOrchestrator {
    CompletableFuture<Map<String, Object>> executeReview(Long taskId, String fullText, SseService sseService);
}
