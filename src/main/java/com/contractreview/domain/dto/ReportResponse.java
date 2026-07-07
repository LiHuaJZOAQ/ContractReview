package com.contractreview.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class ReportResponse {
    private Long taskId;
    private String summary;
    private Map<String, Integer> riskCount;
    private List<RiskItemDto> risks;
    private String generatedAt;
}
