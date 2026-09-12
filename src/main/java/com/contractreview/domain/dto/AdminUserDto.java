package com.contractreview.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminUserDto {
    private Long userId;
    private String username;
    private String role;
    private int reviewQuota;
    private String createdAt;
}
