package com.contractreview.service;

import com.contractreview.domain.dto.SummarizeResult;

import java.util.concurrent.CompletableFuture;

public interface AgentOrchestrator {
    CompletableFuture<SummarizeResult> executeReview(Long taskId, String fullText, SseService sseService);
}
