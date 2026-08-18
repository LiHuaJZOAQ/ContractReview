package com.contractreview.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(condition = SqlCondition.EQUAL)
    private String username;

    private String passwordHash;

    @TableField("review_quota")
    private Integer reviewQuota;

    private String role;

    @TableField("custom_api_url")
    private String customApiUrl;

    @TableField("custom_api_key")
    private String customApiKey;

    @TableField("custom_model")
    private String customModel;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
