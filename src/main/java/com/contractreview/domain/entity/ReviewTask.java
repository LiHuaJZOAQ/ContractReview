package com.contractreview.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review_task")
public class ReviewTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String fileName;
    private Long fileSize;

    private String previewText;
    private String fileUrl;

    private String contractType;
    private String userStance;

    private String status;
    private Integer progress;
    private String errorMsg;

    private Integer totalChunks;
    private Integer reviewedChunks;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
