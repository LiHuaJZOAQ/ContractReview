package com.contractreview.service.impl;

import com.contractreview.domain.dto.ClassifyResult;
import com.contractreview.domain.dto.ScanRiskItem;
import com.contractreview.domain.dto.SummarizeResult;
import com.contractreview.domain.entity.ReviewProcessLog;
import com.contractreview.domain.enums.TaskStatus;
import com.contractreview.mapper.ReviewProcessLogMapper;
import com.contractreview.security.UserContext;
import com.contractreview.service.AgentOrchestrator;
import com.contractreview.service.AgentService;
import com.contractreview.service.RagService;
import com.contractreview.service.ReviewStateMachine;
import com.contractreview.service.SseService;
import com.contractreview.util.ChunkingUtil;
import com.contractreview.util.LogTruncator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestratorImpl implements AgentOrchestrator {

    private final AgentService agentService;
    private final RagService ragService;
    private final ReviewStateMachine stateMachine;
    private final ReviewProcessLogMapper processLogMapper;

    private final Semaphore semaphore = new Semaphore(10);

    @Value("${contract.review.rag.timeout-seconds.reviewing:120}")
    private long reviewingTimeout;

    @Override
    @Async("taskExecutor")
    public CompletableFuture<SummarizeResult> executeReview(Long taskId, String fullText, SseService sseService) {
        try {
            sseService.sendProgress(taskId, "parsing", 10, "正在解析文档...");
            Thread.sleep(500);

            sseService.sendProgress(taskId, "classifying", 20, "正在进行合同分类...");
            ClassifyResult classification = agentService.classifyContract(fullText);
            String contractType = classification.getContractType() != null ? classification.getContractType() : "其他";
            String userStance = classification.getUserStance() != null ? classification.getUserStance() : "其他";
            String strategy = classification.getReviewStrategy() != null ? classification.getReviewStrategy() : "标准审查";
            log.info("Agent A classified: type={}, stance={}", contractType, userStance);
            String agentAResult = "合同类型: " + contractType + "\n立场: " + userStance + "\n策略: " + strategy;
            sseService.sendLlmOutput(taskId, "Agent-A 合同分类", agentAResult);
            saveProcessLog(taskId, "Agent-A 合同分类", agentAResult);
            stateMachine.transition(taskId, TaskStatus.PARSING, TaskStatus.RETRIEVING);

            sseService.sendProgress(taskId, "retrieving", 30, "正在检索相关法条...");
            List<String> chunks = ChunkingUtil.chunkByClause(fullText);
            final List<String> finalChunks = chunks.isEmpty() ? ChunkingUtil.chunkByLength(fullText) : chunks;
            log.info("Chunked into {} parts", finalChunks.size());

            stateMachine.transition(taskId, TaskStatus.RETRIEVING, TaskStatus.REVIEWING);

            final int totalChunks = finalChunks.size();
            List<CompletableFuture<List<ScanRiskItem>>> futures = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                String chunk = finalChunks.get(i);
                final int index = i;
                CompletableFuture<List<ScanRiskItem>> future = UserContext.supplyAsync(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            sseService.sendProgress(taskId, "reviewing",
                                    40 + (index * 50 / totalChunks),
                                    "正在审查第 " + (index + 1) + "/" + totalChunks + " 条...");
                            log.info("Agent B scanning chunk {}/{}", index + 1, totalChunks);
                            List<String> laws = ragService.retrieveRelevantLaws(chunk);
                            List<ScanRiskItem> risks = agentService.scanRisks(chunk, laws, strategy);
                            enrichRiskLaws(risks, laws);
                            if (!risks.isEmpty()) {
                                String output = "第" + (index + 1) + "条发现 " + risks.size() + " 个风险:\n";
                                for (ScanRiskItem r : risks) {
                                    String level = r.getRiskLevel() != null ? r.getRiskLevel() : "LOW";
                                    String desc = r.getDescription() != null ? r.getDescription() : "";
                                    output += "  [" + level + "] "
                                            + desc.substring(0, Math.min(80, desc.length())) + "\n";
                                }
                                sseService.sendLlmOutput(taskId, "Agent-B 条款审查", output);
                                saveProcessLog(taskId, "Agent-B 条款审查", output);
                            }
                            return risks;
                        } finally {
                            semaphore.release();
                        }
                    } catch (Exception e) {
                        log.warn("Agent B failed for chunk {}: {}", index, e.getMessage());
                        return List.<ScanRiskItem>of();
                    }
                });
                futures.add(future);
            }

            List<ScanRiskItem> allRisks = futures.stream()
                    .flatMap(f -> {
                        try {
                            List<ScanRiskItem> risks = f.get();
                            risks.forEach(r -> {
                                if (r.getClauseIndex() == null) r.setClauseIndex(0);
                            });
                            return risks.stream();
                        } catch (Exception e) {
                            log.warn("Failed to get Agent B result", e);
                            return java.util.stream.Stream.empty();
                        }
                    })
                    .collect(Collectors.toList());

            log.info("Agent B completed, total risks: {}", allRisks.size());
            stateMachine.transition(taskId, TaskStatus.REVIEWING, TaskStatus.SUMMARIZING);

            sseService.sendProgress(taskId, "summarizing", 90, "正在生成审查报告...");
            SummarizeResult report = agentService.summarizeReport(allRisks, contractType);
            report.getRiskCount().put("high", report.getRiskCount().getOrDefault("high", 0));
            String summary = report.getSummary() != null ? report.getSummary() : "";
            String agentCSummary = summary.length() > 500 ? summary.substring(0, 500) + "..." : summary;
            sseService.sendLlmOutput(taskId, "Agent-C 汇总报告", agentCSummary);
            saveProcessLog(taskId, "Agent-C 汇总报告", agentCSummary);

            sseService.sendProgress(taskId, "completed", 100, "审查完成");
            return CompletableFuture.completedFuture(report);

        } catch (Exception e) {
            log.error("Review orchestration failed for task {}: {}", taskId, LogTruncator.truncate(e.getMessage(), 200));
            sseService.sendError(taskId, "审查失败: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    private void saveProcessLog(Long taskId, String agent, String content) {
        try {
            ReviewProcessLog logEntry = new ReviewProcessLog();
            logEntry.setTaskId(taskId);
            logEntry.setAgent(agent);
            logEntry.setContent(content);
            logEntry.setCreatedAt(LocalDateTime.now());
            processLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("Failed to save process log for task {}: {}", taskId, e.getMessage());
        }
    }

    private void enrichRiskLaws(List<ScanRiskItem> risks, List<String> ragLaws) {
        for (ScanRiskItem risk : risks) {
            List<String> riskLaws = risk.getRelatedLaws();
            if (riskLaws == null || riskLaws.isEmpty()) continue;
            List<String> enriched = new ArrayList<>();
            for (String law : riskLaws) {
                if (law.contains("：") || law.contains(":")) {
                    enriched.add(law);
                } else {
                    String full = ragLaws.stream()
                            .filter(rl -> rl.startsWith(law))
                            .findFirst()
                            .orElse(law);
                    enriched.add(full);
                }
            }
            risk.setRelatedLaws(enriched);
        }
    }
}
