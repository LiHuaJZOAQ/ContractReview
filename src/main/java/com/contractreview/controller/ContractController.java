package com.contractreview.controller;

import com.contractreview.aop.AuditLog;
import com.contractreview.common.R;
import com.contractreview.domain.dto.*;
import com.contractreview.security.UserContext;
import com.contractreview.service.ContractService;
import com.contractreview.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contract")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;
    private final SseService sseService;

    @PostMapping("/upload")
    @AuditLog(action = "UPLOAD")
    public R<UploadResponse> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(defaultValue = "true") boolean desensitize) {
        Long userId = UserContext.getUserId();
        UploadResponse response = contractService.upload(file, userId, desensitize);
        return R.ok(response);
    }

    @PostMapping("/{taskId}/submit")
    @AuditLog(action = "SUBMIT")
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
    @AuditLog(action = "VIEW_REPORT")
    public R<ReportResponse> getReport(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        ReportResponse response = contractService.getReport(taskId, userId);
        return R.ok(response);
    }

    @GetMapping({"/history", "/history/{status}"})
    public R<HistoryResponse> getHistory(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size,
                                          @PathVariable(required = false) String status) {
        Long userId = UserContext.getUserId();
        HistoryResponse response = contractService.getHistory(userId, page, size, status);
        return R.ok(response);
    }

    @PostMapping("/{taskId}/retry")
    @AuditLog(action = "RETRY")
    public R<Void> retry(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        contractService.retry(taskId, userId);
        return R.ok();
    }

    @GetMapping("/{taskId}/progress")
    public SseEmitter progress(@PathVariable Long taskId) {
        return sseService.createEmitter(taskId);
    }

    @GetMapping("/{taskId}/text")
    public R<String> getPreviewText(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        String text = contractService.getPreviewText(taskId, userId);
        return R.ok(text);
    }

    @GetMapping("/{taskId}/logs")
    public R<List<ContractService.ReviewProcessLogDto>> getProcessLogs(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        List<ContractService.ReviewProcessLogDto> logs = contractService.getProcessLogs(taskId, userId);
        return R.ok(logs);
    }
}
