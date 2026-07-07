package com.contractreview.domain.enums;

public enum ErrorCode {
    FORMAT_NOT_SUPPORTED(1001, "文件格式不支持，仅允许 PDF/Word 格式"),
    FILE_SIZE_EXCEEDED(1002, "文件大小超出限制，最大 20MB"),
    QUOTA_INSUFFICIENT(1003, "审查次数不足"),
    TASK_NOT_FOUND(1004, "任务不存在"),
    INVALID_STATE(1005, "当前任务状态不允许此操作"),
    LLM_API_FAILED(1006, "LLM API 调用失败"),
    RATE_LIMITED(1008, "请求频率超限"),
    TASK_TIMEOUT(1009, "任务执行超时");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
