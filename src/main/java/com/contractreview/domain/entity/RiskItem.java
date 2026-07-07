package com.contractreview.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("risk_item")
public class RiskItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private Integer clauseIndex;
    private String clauseContent;

    private String riskLevel;
    private String riskType;
    private String description;
    private String suggestion;
    private String relatedLaws;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
