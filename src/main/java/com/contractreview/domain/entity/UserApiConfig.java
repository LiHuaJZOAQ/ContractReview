package com.contractreview.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_api_config")
public class UserApiConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String configName;

    private String apiUrl;

    private String apiKey;

    private String model;

    private Integer isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
