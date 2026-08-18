package com.contractreview.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ClassifyResult {
    @JsonProperty("contractType")
    private String contractType;

    @JsonProperty("userStance")
    private String userStance;

    @JsonProperty("reviewStrategy")
    private String reviewStrategy;
}
