package com.contractreview.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RiskItemDto {
    private Integer clauseIndex;
    private String clauseContent;
    private String riskLevel;
    private String riskType;
    private String description;
    private String suggestion;
    private List<String> relatedLaws;
}
