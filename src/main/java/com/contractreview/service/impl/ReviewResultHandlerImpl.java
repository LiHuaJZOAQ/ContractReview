package com.contractreview.service.impl;

import com.contractreview.domain.dto.ReviewMessage;
import com.contractreview.domain.dto.ScanRiskItem;
import com.contractreview.domain.dto.SummarizeResult;
import com.contractreview.domain.entity.ReviewReport;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.entity.RiskItem;
import com.contractreview.domain.entity.User;
import com.contractreview.domain.enums.TaskStatus;
import com.contractreview.mapper.ReviewReportMapper;
import com.contractreview.mapper.ReviewTaskMapper;
import com.contractreview.mapper.RiskItemMapper;
import com.contractreview.mapper.UserMapper;
import com.contractreview.service.ReviewResultHandler;
import com.contractreview.service.ReviewStateMachine;
import com.contractreview.service.SseService;
import com.contractreview.util.LogTruncator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewResultHandlerImpl implements ReviewResultHandler {

    private final ReviewStateMachine stateMachine;
    private final ReviewTaskMapper taskMapper;
    private final ReviewReportMapper reportMapper;
    private final RiskItemMapper riskItemMapper;
    private final UserMapper userMapper;
    private final SseService sseService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public void handleSuccess(Long taskId, SummarizeResult result) {
        try {
            ReviewTask task = taskMapper.selectById(taskId);
            if (task == null) {
                log.error("Task {} not found on handleSuccess", taskId);
                return;
            }

            stateMachine.transition(taskId, TaskStatus.SUMMARIZING, TaskStatus.SUCCESS);
            saveReviewResult(taskId, result);

            ReviewTask updatedTask = taskMapper.selectById(taskId);
            updatedTask.setContractType(result.getContractType());
            updatedTask.setUserStance(result.getUserStance());
            taskMapper.updateById(updatedTask);

            sseService.sendComplete(taskId, taskId.toString());
            log.info("Review completed successfully for task {}", taskId);
        } catch (Exception e) {
            log.error("Failed to save review result for task {}: {}", taskId, LogTruncator.truncate(e.getMessage(), 200));
            handleFailure(taskId, null, new ReviewMessage(taskId, null, 0), e);
        }
    }

    @Override
    @Transactional
    public void handleFailure(Long taskId, Long userId, ReviewMessage message, Throwable throwable) {
        log.error("Review failed for task {}: {}", taskId, LogTruncator.truncate(throwable.getMessage(), 200));

        try {
            ReviewTask task = taskMapper.selectById(taskId);
            if (task == null || TaskStatus.SUCCESS.name().equals(task.getStatus())) return;

            TaskStatus currentStatus = TaskStatus.valueOf(task.getStatus());
            stateMachine.transition(taskId, currentStatus, TaskStatus.FAILED);

            ReviewTask failedTask = taskMapper.selectById(taskId);
            failedTask.setErrorMsg(throwable.getMessage());
            taskMapper.updateById(failedTask);

            sseService.sendError(taskId, "审查失败: " + throwable.getMessage());

            Long actualUserId = userId != null ? userId : task.getUserId();
            if (actualUserId != null) {
                String quotaKey = "user:quota:" + actualUserId;
                redisTemplate.opsForValue().increment(quotaKey);
                User user = userMapper.selectById(actualUserId);
                if (user != null) {
                    user.setReviewQuota(user.getReviewQuota() + 1);
                    userMapper.updateById(user);
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle failure for task {}: {}", taskId, LogTruncator.truncate(e.getMessage(), 200));
        }
    }

    private void saveReviewResult(Long taskId, SummarizeResult result) {
        String summary = result.getSummary() != null ? result.getSummary() : "";
        List<ScanRiskItem> risks = result.getRisks() != null ? result.getRisks() : List.of();

        int high = 0, medium = 0, low = 0;
        for (ScanRiskItem risk : risks) {
            RiskItem item = new RiskItem();
            item.setTaskId(taskId);
            item.setClauseIndex(risk.getClauseIndex() != null ? risk.getClauseIndex() : 0);
            item.setClauseContent(risk.getClauseContent() != null ? risk.getClauseContent() : "");
            item.setRiskLevel(risk.getRiskLevel() != null ? risk.getRiskLevel() : "LOW");
            item.setRiskType(risk.getRiskType() != null ? risk.getRiskType() : "");
            item.setDescription(risk.getDescription() != null ? risk.getDescription() : "");
            item.setSuggestion(risk.getSuggestion() != null ? risk.getSuggestion() : "");
            if (risk.getRelatedLaws() != null && !risk.getRelatedLaws().isEmpty()) {
                try {
                    item.setRelatedLaws(objectMapper.writeValueAsString(risk.getRelatedLaws()));
                } catch (Exception e) {
                    log.warn("Failed to serialize relatedLaws", e);
                }
            }
            riskItemMapper.insert(item);

            String level = (risk.getRiskLevel() != null ? risk.getRiskLevel() : "LOW").toUpperCase();
            switch (level) {
                case "HIGH": high++; break;
                case "MEDIUM": medium++; break;
                default: low++;
            }
        }

        ReviewReport report = new ReviewReport();
        report.setTaskId(taskId);
        report.setSummary(summary);
        report.setRiskCountHigh(high);
        report.setRiskCountMedium(medium);
        report.setRiskCountLow(low);
        try {
            report.setReportJson(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            report.setReportJson("{}");
        }
        report.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(report);
    }
}
