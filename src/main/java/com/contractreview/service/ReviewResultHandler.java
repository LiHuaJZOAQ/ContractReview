package com.contractreview.service;

import com.contractreview.domain.dto.ReviewMessage;
import com.contractreview.domain.dto.SummarizeResult;

public interface ReviewResultHandler {
    void handleSuccess(Long taskId, SummarizeResult result);
    void handleFailure(Long taskId, Long userId, ReviewMessage message, Throwable throwable);
}
