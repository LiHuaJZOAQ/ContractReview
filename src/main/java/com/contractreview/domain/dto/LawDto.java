package com.contractreview.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LawDto {
    private Long id;
    private String title;
    private String category;
    private String content;
    private boolean enabled;
    private String createdAt;
    private String updatedAt;
}
