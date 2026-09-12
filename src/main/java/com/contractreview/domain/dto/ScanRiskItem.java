package com.contractreview.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ScanRiskItem {
    @JsonProperty("clauseIndex")
    private Integer clauseIndex;

    @JsonProperty("clauseContent")
    private String clauseContent;

    @JsonProperty("riskLevel")
    private String riskLevel;

    @JsonProperty("riskType")
    private String riskType;

    @JsonProperty("description")
    private String description;

    @JsonProperty("suggestion")
    private String suggestion;

    @JsonProperty("relatedLaws")
    private List<String> relatedLaws;
}
