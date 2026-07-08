package com.contractreview.service;

public interface ReviewStateMachine {
    void transition(Long taskId, String currentStatus, String targetStatus);
    void validateTransition(String currentStatus, String targetStatus);
}
