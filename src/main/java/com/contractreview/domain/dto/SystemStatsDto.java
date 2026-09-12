package com.contractreview.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SystemStatsDto {
    private long totalUsers;
    private long totalTasks;
    private long successTasks;
    private long failedTasks;
    private long processingTasks;
    private long totalLaws;
}
