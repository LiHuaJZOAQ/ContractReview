package com.contractreview.controller;

import com.contractreview.aop.AuditLog;
import com.contractreview.common.R;
import com.contractreview.domain.dto.*;
import com.contractreview.security.UserContext;
import com.contractreview.service.ContractService;
import com.contractreview.service.SseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contract")
@RequiredArgsConstructor
@Tag(name = "合同审查", description = "合同上传、审查提交、报告查询")
public class ContractController {

    private final ContractService contractService;
    private final SseService sseService;

    @PostMapping("/upload")
    @AuditLog(action = "UPLOAD")
    @Operation(summary = "上传合同文件", description = "支持 PDF、Word(.docx)、纯文本(.txt) 格式，最大 20MB",
            responses = {
                    @ApiResponse(responseCode = "200", description = "上传成功"),
                    @ApiResponse(responseCode = "400", description = "文件为空或超过大小限制")
            })
    public R<UploadResponse> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(defaultValue = "true") boolean desensitize) {
        Long userId = UserContext.getUserId();
        if (file.isEmpty()) {
            throw new com.contractreview.common.BusinessException(400, "文件不能为空");
        }
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new com.contractreview.common.BusinessException(400, "文件大小不能超过20MB");
        }
        UploadResponse response = contractService.upload(file, userId, desensitize);
        return R.ok(response);
    }

    @PostMapping("/paste")
    @AuditLog(action = "PASTE")
    @Operation(summary = "粘贴合同文本", description = "直接粘贴合同文本进行审查，无需上传文件",
            responses = {
                    @ApiResponse(responseCode = "200", description = "提交成功"),
                    @ApiResponse(responseCode = "400", description = "文本为空或超过长度限制")
            })
    public R<UploadResponse> pasteText(@Valid @RequestBody PasteRequest request,
                                       @RequestParam(defaultValue = "true") boolean desensitize) {
        Long userId = UserContext.getUserId();
        UploadResponse response = contractService.pasteText(request.getText(), userId, desensitize);
        return R.ok(response);
    }

    @PostMapping("/{taskId}/submit")
    @AuditLog(action = "SUBMIT")
    @Operation(summary = "提交审查", description = "确认上传的合同并启动多 Agent 审查流程",
            responses = {
                    @ApiResponse(responseCode = "200", description = "提交成功"),
                    @ApiResponse(responseCode = "400", description = "任务状态不允许提交")
            })
    public R<Void> submit(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        contractService.submit(taskId, userId);
        return R.ok();
    }

    @GetMapping("/{taskId}/status")
    @Operation(summary = "查询审查状态", description = "获取指定任务的当前审查状态")
    public R<StatusResponse> getStatus(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        StatusResponse response = contractService.getStatus(taskId, userId);
        return R.ok(response);
    }

    @GetMapping("/{taskId}/report")
    @AuditLog(action = "VIEW_REPORT")
    @Operation(summary = "获取审查报告", description = "获取完整的风险审查报告，包含风险项和摘要")
    public R<ReportResponse> getReport(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        ReportResponse response = contractService.getReport(taskId, userId);
        return R.ok(response);
    }

    @GetMapping({"/history", "/history/{status}"})
    @Operation(summary = "查询审查历史", description = "分页查询当前用户的审查历史记录")
    public R<HistoryResponse> getHistory(@RequestParam(defaultValue = "1") @Min(1) int page,
                                          @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
                                          @PathVariable(required = false) String status) {
        Long userId = UserContext.getUserId();
        HistoryResponse response = contractService.getHistory(userId, page, size, status);
        return R.ok(response);
    }

    @PostMapping("/{taskId}/retry")
    @AuditLog(action = "RETRY")
    @Operation(summary = "重试失败任务", description = "重新提交失败的审查任务进行重试",
            responses = {
                    @ApiResponse(responseCode = "200", description = "重试已提交"),
                    @ApiResponse(responseCode = "400", description = "任务未处于失败状态")
            })
    public R<Void> retry(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        contractService.retry(taskId, userId);
        return R.ok();
    }

    @GetMapping("/{taskId}/progress")
    @Operation(summary = "实时进度流", description = "NDJSON 流式推送审查进度事件")
    public ResponseBodyEmitter progress(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        contractService.getStatus(taskId, userId);
        return sseService.createEmitter(taskId);
    }

    @GetMapping("/{taskId}/text")
    @Operation(summary = "获取合同预览文本", description = "返回脱敏后的合同文本内容")
    public R<String> getPreviewText(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        String text = contractService.getPreviewText(taskId, userId);
        return R.ok(text);
    }

    @GetMapping("/{taskId}/logs")
    @Operation(summary = "获取审查过程日志", description = "返回每个 Agent 的执行过程日志")
    public R<List<ContractService.ReviewProcessLogDto>> getProcessLogs(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        List<ContractService.ReviewProcessLogDto> logs = contractService.getProcessLogs(taskId, userId);
        return R.ok(logs);
    }
}
