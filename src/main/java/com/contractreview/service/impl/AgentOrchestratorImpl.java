package com.contractreview.service.impl;

import com.contractreview.service.AgentOrchestrator;
import com.contractreview.service.AgentService;
import com.contractreview.service.RagService;
import com.contractreview.service.SseService;
import com.contractreview.util.ChunkingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestratorImpl implements AgentOrchestrator {

    private final AgentService agentService;
    private final RagService ragService;

    private final Semaphore semaphore = new Semaphore(10);

    @Value("${contract.review.rag.timeout-seconds.reviewing:120}")
    private long reviewingTimeout;

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Map<String, Object>> executeReview(Long taskId, String fullText, SseService sseService) {
        try {
            sseService.sendProgress(taskId, "parsing", 10, "正在解析文档...");
            Thread.sleep(500);

            sseService.sendProgress(taskId, "classifying", 20, "正在进行合同分类...");
            Map<String, String> classification = agentService.classifyContract(fullText);
            String contractType = classification.getOrDefault("contractType", "其他");
            String userStance = classification.getOrDefault("userStance", "其他");
            String strategy = classification.getOrDefault("reviewStrategy", "标准审查");
            log.info("Agent A classified: type={}, stance={}", contractType, userStance);

            sseService.sendProgress(taskId, "retrieving", 30, "正在检索相关法条...");
            List<String> chunks = ChunkingUtil.chunkByClause(fullText);
            final List<String> finalChunks = chunks.isEmpty() ? ChunkingUtil.chunkByLength(fullText) : chunks;
            log.info("Chunked into {} parts", finalChunks.size());

            final int totalChunks = finalChunks.size();
            List<CompletableFuture<List<Map<String, Object>>>> futures = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                String chunk = finalChunks.get(i);
                final int index = i;
                CompletableFuture<List<Map<String, Object>>> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            sseService.sendProgress(taskId, "reviewing",
                                    40 + (index * 50 / totalChunks),
                                    "正在审查第 " + (index + 1) + "/" + totalChunks + " 条...");
                            log.info("Agent B scanning chunk {}/{}", index + 1, totalChunks);
                            List<String> laws = ragService.retrieveRelevantLaws(chunk);
                            return agentService.scanRisks(chunk, laws, strategy);
                        } finally {
                            semaphore.release();
                        }
                    } catch (Exception e) {
                        log.warn("Agent B failed for chunk {}: {}", index, e.getMessage());
                        return List.of();
                    }
                });
                futures.add(future);
            }

            List<Map<String, Object>> allRisks = futures.stream()
                    .flatMap(f -> {
                        try {
                            List<Map<String, Object>> risks = f.get();
                            risks.forEach(r -> r.putIfAbsent("clauseIndex", 0));
                            return risks.stream();
                        } catch (Exception e) {
                            log.warn("Failed to get Agent B result", e);
                            return java.util.stream.Stream.empty();
                        }
                    })
                    .collect(Collectors.toList());

            log.info("Agent B completed, total risks: {}", allRisks.size());

            sseService.sendProgress(taskId, "summarizing", 90, "正在生成审查报告...");
            Map<String, Object> report = agentService.summarizeReport(allRisks, contractType);
            report.put("userStance", userStance);

            sseService.sendProgress(taskId, "completed", 100, "审查完成");
            return CompletableFuture.completedFuture(report);

        } catch (Exception e) {
            log.error("Review orchestration failed for task {}: {}", taskId, e.getMessage());
            sseService.sendError(taskId, "审查失败: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
}
