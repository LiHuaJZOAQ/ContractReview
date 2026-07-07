package com.contractreview.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review_report")
public class ReviewReport {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private String summary;
    private Integer riskCountHigh;
    private Integer riskCountMedium;
    private Integer riskCountLow;
    private String reportJson;
    private String pdfUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
