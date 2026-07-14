package com.contractreview.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review_process_log")
public class ReviewProcessLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private String agent;
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
