package com.contractreview.service;

import com.contractreview.domain.enums.TaskStatus;

public interface ReviewStateMachine {
    void transition(Long taskId, TaskStatus currentStatus, TaskStatus targetStatus);
    void validateTransition(TaskStatus currentStatus, TaskStatus targetStatus);
}
