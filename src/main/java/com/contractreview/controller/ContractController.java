package com.contractreview.controller;

import com.contractreview.common.R;
import com.contractreview.domain.dto.*;
import com.contractreview.security.UserContext;
import com.contractreview.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/contract")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @PostMapping("/upload")
    public R<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getUserId();
        UploadResponse response = contractService.upload(file, userId);
        return R.ok(response);
    }

    @PostMapping("/{taskId}/submit")
    public R<Void> submit(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        contractService.submit(taskId, userId);
        return R.ok();
    }

    @GetMapping("/{taskId}/status")
    public R<StatusResponse> getStatus(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        StatusResponse response = contractService.getStatus(taskId, userId);
        return R.ok(response);
    }

    @GetMapping("/{taskId}/report")
    public R<ReportResponse> getReport(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        ReportResponse response = contractService.getReport(taskId, userId);
        return R.ok(response);
    }

    @GetMapping("/history")
    public R<HistoryResponse> getHistory(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContext.getUserId();
        HistoryResponse response = contractService.getHistory(userId, page, size);
        return R.ok(response);
    }

    @PostMapping("/{taskId}/retry")
    public R<Void> retry(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        contractService.retry(taskId, userId);
        return R.ok();
    }
}
