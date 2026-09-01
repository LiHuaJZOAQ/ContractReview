package com.contractreview.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OperationLogDto {
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private Long taskId;
    private String detail;
    private String ipAddress;
    private String createdAt;
}
