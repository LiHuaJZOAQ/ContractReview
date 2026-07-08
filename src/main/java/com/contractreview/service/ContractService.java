package com.contractreview.service;

import com.contractreview.domain.dto.*;
import org.springframework.web.multipart.MultipartFile;

public interface ContractService {
    UploadResponse upload(MultipartFile file, Long userId, boolean desensitize);
    void submit(Long taskId, Long userId);
    StatusResponse getStatus(Long taskId, Long userId);
    ReportResponse getReport(Long taskId, Long userId);
    HistoryResponse getHistory(Long userId, int page, int size);
    void retry(Long taskId, Long userId);
}
