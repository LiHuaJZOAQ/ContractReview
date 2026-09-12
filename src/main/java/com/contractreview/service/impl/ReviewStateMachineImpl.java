package com.contractreview.service.impl;

import com.contractreview.common.BusinessException;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.enums.ErrorCode;
import com.contractreview.domain.enums.TaskStatus;
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

    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS = Map.of(
            TaskStatus.PENDING, Set.of(TaskStatus.PARSING),
            TaskStatus.PARSING, Set.of(TaskStatus.RETRIEVING, TaskStatus.FAILED),
            TaskStatus.RETRIEVING, Set.of(TaskStatus.REVIEWING, TaskStatus.RETRIEVING, TaskStatus.FAILED),
            TaskStatus.REVIEWING, Set.of(TaskStatus.SUMMARIZING, TaskStatus.REVIEWING, TaskStatus.FAILED),
            TaskStatus.SUMMARIZING, Set.of(TaskStatus.SUCCESS, TaskStatus.FAILED),
            TaskStatus.FAILED, Set.of(TaskStatus.PENDING),
            TaskStatus.SUCCESS, Set.of()
    );

    @Override
    @Transactional
    public void transition(Long taskId, TaskStatus currentStatus, TaskStatus targetStatus) {
        validateTransition(currentStatus, targetStatus);

        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        if (!currentStatus.name().equals(task.getStatus())) {
            log.warn("State mismatch: expected {} but actual is {} for task {}",
                    currentStatus, task.getStatus(), taskId);
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "当前状态不匹配: 期望 " + currentStatus + "，实际 " + task.getStatus());
        }

        task.setStatus(targetStatus.name());

        if ((targetStatus == TaskStatus.SUCCESS || targetStatus == TaskStatus.FAILED) && task.getCompletedAt() == null) {
            task.setCompletedAt(LocalDateTime.now());
        }

        switch (targetStatus) {
            case PARSING -> task.setProgress(5);
            case RETRIEVING -> task.setProgress(20);
            case REVIEWING -> task.setProgress(40);
            case SUMMARIZING -> task.setProgress(80);
            case SUCCESS -> task.setProgress(100);
            case FAILED -> task.setProgress(-1);
            case PENDING -> {
                task.setProgress(0);
                task.setErrorMsg(null);
                task.setCompletedAt(null);
            }
        }

        taskMapper.updateById(task);
        log.info("State transition: taskId={} {} -> {}", taskId, currentStatus, targetStatus);
    }

    @Override
    public void validateTransition(TaskStatus currentStatus, TaskStatus targetStatus) {
        Set<TaskStatus> allowed = TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "非法状态转换: " + currentStatus + " → " + targetStatus);
        }
    }
}
