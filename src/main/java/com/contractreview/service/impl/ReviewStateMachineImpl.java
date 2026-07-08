package com.contractreview.service.impl;

import com.contractreview.common.BusinessException;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.enums.ErrorCode;
import com.contractreview.mapper.ReviewTaskMapper;
import com.contractreview.service.ReviewStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewStateMachineImpl implements ReviewStateMachine {

    private final ReviewTaskMapper taskMapper;

    private static final Set<String> VALID_STATUSES = Set.of(
            "PENDING", "PARSING", "RETRIEVING", "REVIEWING", "SUMMARIZING", "SUCCESS", "FAILED"
    );

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PENDING", Set.of("PARSING"),
            "PARSING", Set.of("RETRIEVING", "FAILED"),
            "RETRIEVING", Set.of("REVIEWING", "RETRIEVING", "FAILED"),
            "REVIEWING", Set.of("SUMMARIZING", "REVIEWING", "FAILED"),
            "SUMMARIZING", Set.of("SUCCESS", "FAILED"),
            "FAILED", Set.of("PENDING"),
            "SUCCESS", Set.of()
    );

    @Override
    @Transactional
    public void transition(Long taskId, String currentStatus, String targetStatus) {
        validateTransition(currentStatus, targetStatus);

        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        if (!currentStatus.equals(task.getStatus())) {
            log.warn("State mismatch: expected {} but actual is {} for task {}",
                    currentStatus, task.getStatus(), taskId);
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "当前状态不匹配: 期望 " + currentStatus + "，实际 " + task.getStatus());
        }

        task.setStatus(targetStatus);

        if (Set.of("SUCCESS", "FAILED").contains(targetStatus) && task.getCompletedAt() == null) {
            task.setCompletedAt(LocalDateTime.now());
        }

        if ("PARSING".equals(targetStatus)) {
            task.setProgress(5);
        } else if ("RETRIEVING".equals(targetStatus)) {
            task.setProgress(20);
        } else if ("REVIEWING".equals(targetStatus)) {
            task.setProgress(40);
        } else if ("SUMMARIZING".equals(targetStatus)) {
            task.setProgress(80);
        } else if ("SUCCESS".equals(targetStatus)) {
            task.setProgress(100);
        } else if ("FAILED".equals(targetStatus)) {
            task.setProgress(-1);
        } else if ("PENDING".equals(targetStatus)) {
            task.setProgress(0);
            task.setErrorMsg(null);
            task.setCompletedAt(null);
        }

        taskMapper.updateById(task);
        log.info("State transition: taskId={} {} -> {}", taskId, currentStatus, targetStatus);
    }

    @Override
    public void validateTransition(String currentStatus, String targetStatus) {
        if (!VALID_STATUSES.contains(currentStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "无效的当前状态: " + currentStatus);
        }
        if (!VALID_STATUSES.contains(targetStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "无效的目标状态: " + targetStatus);
        }

        Set<String> allowed = TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "非法状态转换: " + currentStatus + " → " + targetStatus);
        }
    }
}
