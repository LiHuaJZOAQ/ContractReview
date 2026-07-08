package com.contractreview.service.impl;

import com.contractreview.config.RabbitMqConfig;
import com.contractreview.domain.dto.ReviewMessage;
import com.contractreview.domain.entity.ReviewReport;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.entity.RiskItem;
import com.contractreview.mapper.ReviewReportMapper;
import com.contractreview.mapper.ReviewTaskMapper;
import com.contractreview.mapper.RiskItemMapper;
import com.contractreview.mapper.UserMapper;
import com.contractreview.service.AgentOrchestrator;
import com.contractreview.service.ReviewStateMachine;
import com.contractreview.service.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewMessageListener {

    private final ReviewStateMachine stateMachine;
    private final AgentOrchestrator agentOrchestrator;
    private final SseService sseService;
    private final ReviewTaskMapper taskMapper;
    private final ReviewReportMapper reportMapper;
    private final RiskItemMapper riskItemMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    @Value("${contract.review.max-retry-count:3}")
    private int maxRetryCount;

    @RabbitListener(queues = "${contract.review.queue.name:contract.review.queue}")
    public void handleReviewMessage(ReviewMessage message, Message amqpMessage) {
        Long taskId = message.getTaskId();
        Long userId = message.getUserId();
        log.info("MQ consumer received review message: taskId={}, userId={}, retryCount={}",
                taskId, userId, message.getRetryCount());

        try {
            stateMachine.transition(taskId, "PENDING", "PARSING");

            ReviewTask task = taskMapper.selectById(taskId);
            if (task == null) {
                log.error("Task not found: {}", taskId);
                stateMachine.transition(taskId, "PARSING", "FAILED");
                sseService.sendError(taskId, "任务不存在");
                return;
            }

            String fullText = task.getPreviewText();
            Long finalUserId = userId;
            agentOrchestrator.executeReview(taskId, fullText, sseService)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            handleFailure(taskId, finalUserId, message, throwable);
                        } else {
                            handleSuccess(taskId, result);
                        }
                    });

        } catch (Exception e) {
            log.error("Failed to start review for task {}: {}", taskId, e.getMessage());
            handleFailure(taskId, userId, message, e);
        }
    }

    @RabbitListener(queues = "${contract.review.dlx.name:contract.review.dlx}")
    public void handleDlxMessage(ReviewMessage message) {
        Long taskId = message.getTaskId();
        log.info("DLX consumer received: taskId={}, retryCount={}", taskId, message.getRetryCount());

        if (message.getRetryCount() < maxRetryCount) {
            message.setRetryCount(message.getRetryCount() + 1);
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EXCHANGE_REVIEW,
                    RabbitMqConfig.ROUTING_KEY,
                    message);
            log.info("Re-queued task {} for retry {}/{}", taskId, message.getRetryCount(), maxRetryCount);
        } else {
            try {
                ReviewTask task = taskMapper.selectById(taskId);
                if (task == null || "FAILED".equals(task.getStatus())
                        || "SUCCESS".equals(task.getStatus())) {
                    log.warn("Task {} already in terminal state, skipping DLX", taskId);
                    return;
                }

                stateMachine.transition(taskId, task.getStatus(), "FAILED");
                task.setErrorMsg("重试次数已达上限 (" + maxRetryCount + " 次)");
                taskMapper.updateById(task);

                String quotaKey = "user:quota:" + task.getUserId();
                redisTemplate.opsForValue().increment(quotaKey);
                com.contractreview.domain.entity.User user = userMapper.selectById(task.getUserId());
                if (user != null) {
                    user.setReviewQuota(user.getReviewQuota() + 1);
                    userMapper.updateById(user);
                }

                sseService.sendError(taskId, "审查失败，重试次数已达上限");
                log.warn("Task {} failed after {} retries", taskId, maxRetryCount);
            } catch (Exception e) {
                log.error("Failed to mark task {} as FAILED: {}", taskId, e.getMessage());
            }
        }
    }

    @Transactional
    public void handleSuccess(Long taskId, Map<String, Object> result) {
        try {
            ReviewTask task = taskMapper.selectById(taskId);
            if (task == null) {
                log.error("Task {} not found on handleSuccess", taskId);
                return;
            }

            stateMachine.transition(taskId, "SUMMARIZING", "SUCCESS");
            saveReviewResult(taskId, result);

            task.setContractType((String) result.getOrDefault("contractType", null));
            task.setUserStance((String) result.getOrDefault("userStance", null));
            taskMapper.updateById(task);

            sseService.sendComplete(taskId, taskId.toString());
            log.info("Review completed successfully for task {}", taskId);
        } catch (Exception e) {
            log.error("Failed to save review result for task {}: {}", taskId, e.getMessage());
            handleFailure(taskId, null, new ReviewMessage(taskId, null, 0), e);
        }
    }

    public void handleFailure(Long taskId, Long userId, ReviewMessage message, Throwable throwable) {
        log.error("Review failed for task {}: {}", taskId, throwable.getMessage());

        try {
            ReviewTask task = taskMapper.selectById(taskId);
            if (task == null || "SUCCESS".equals(task.getStatus())) return;

            String currentStatus = task.getStatus();
            stateMachine.transition(taskId, currentStatus, "FAILED");

            task.setErrorMsg(throwable.getMessage());
            taskMapper.updateById(task);

            sseService.sendError(taskId, "审查失败: " + throwable.getMessage());

            Long actualUserId = userId != null ? userId : task.getUserId();
            if (actualUserId != null) {
                String quotaKey = "user:quota:" + actualUserId;
                redisTemplate.opsForValue().increment(quotaKey);
                com.contractreview.domain.entity.User user = userMapper.selectById(actualUserId);
                if (user != null) {
                    user.setReviewQuota(user.getReviewQuota() + 1);
                    userMapper.updateById(user);
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle failure for task {}: {}", taskId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void saveReviewResult(Long taskId, Map<String, Object> result) {
        String summary = (String) result.getOrDefault("summary", "");

        Map<String, Integer> riskCount;
        Object rc = result.get("riskCount");
        if (rc instanceof Map) {
            riskCount = (Map<String, Integer>) rc;
        } else {
            riskCount = new HashMap<>();
            riskCount.put("high", 0);
            riskCount.put("medium", 0);
            riskCount.put("low", 0);
        }

        List<Map<String, Object>> risks;
        Object r = result.get("risks");
        if (r instanceof List) {
            risks = (List<Map<String, Object>>) r;
        } else {
            risks = List.of();
        }

        for (Map<String, Object> riskMap : risks) {
            RiskItem item = new RiskItem();
            item.setTaskId(taskId);
            item.setClauseIndex((Integer) riskMap.getOrDefault("clauseIndex", 0));
            item.setClauseContent((String) riskMap.getOrDefault("clauseContent", ""));
            item.setRiskLevel((String) riskMap.getOrDefault("riskLevel", "LOW"));
            item.setRiskType((String) riskMap.getOrDefault("riskType", ""));
            item.setDescription((String) riskMap.getOrDefault("description", ""));
            item.setSuggestion((String) riskMap.getOrDefault("suggestion", ""));
            Object laws = riskMap.get("relatedLaws");
            if (laws instanceof List) {
                try {
                    item.setRelatedLaws(objectMapper.writeValueAsString(laws));
                } catch (Exception e) {
                    log.warn("Failed to serialize relatedLaws", e);
                }
            }
            riskItemMapper.insert(item);
        }

        ReviewReport report = new ReviewReport();
        report.setTaskId(taskId);
        report.setSummary(summary);
        report.setRiskCountHigh(riskCount.getOrDefault("high", 0));
        report.setRiskCountMedium(riskCount.getOrDefault("medium", 0));
        report.setRiskCountLow(riskCount.getOrDefault("low", 0));
        try {
            report.setReportJson(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            report.setReportJson("{}");
        }
        report.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(report);
    }
}
