package com.contractreview.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class HistoryResponse {
    private List<HistoryItem> tasks;
    private long total;
    private int page;
    private int size;

    @Data
    @AllArgsConstructor
    public static class HistoryItem {
        private Long taskId;
        private String fileName;
        private String status;
        private Integer progress;
        private String createdAt;
    }
}
