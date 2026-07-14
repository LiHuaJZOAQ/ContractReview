package com.contractreview.service;

import com.contractreview.domain.dto.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ContractService {
    UploadResponse upload(MultipartFile file, Long userId, boolean desensitize);
    void submit(Long taskId, Long userId);
    StatusResponse getStatus(Long taskId, Long userId);
    ReportResponse getReport(Long taskId, Long userId);
    HistoryResponse getHistory(Long userId, int page, int size, String status);
    void retry(Long taskId, Long userId);
    String getPreviewText(Long taskId, Long userId);
    List<ReviewProcessLogDto> getProcessLogs(Long taskId, Long userId);

    @Data
    @AllArgsConstructor
    class ReviewProcessLogDto {
        private String agent;
        private String content;
        private String createdAt;
    }
}
