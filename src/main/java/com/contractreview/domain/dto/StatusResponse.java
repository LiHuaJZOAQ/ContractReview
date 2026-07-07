package com.contractreview.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StatusResponse {
    private Long taskId;
    private String status;
    private Integer progress;
}
