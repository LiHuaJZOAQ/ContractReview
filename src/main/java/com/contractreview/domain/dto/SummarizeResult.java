package com.contractreview.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SummarizeResult {
    @JsonProperty("summary")
    private String summary;

    @JsonProperty("riskCount")
    private Map<String, Integer> riskCount;

    @JsonProperty("risks")
    private List<ScanRiskItem> risks;

    private String contractType;
    private String userStance;
}
