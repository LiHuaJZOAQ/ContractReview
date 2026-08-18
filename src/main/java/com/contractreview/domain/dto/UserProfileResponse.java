package com.contractreview.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserProfileResponse {
    private Long userId;
    private String username;
    private int reviewQuota;
    private int quotaTotal;
    private String role;
    private String customApiUrl;
    private boolean hasCustomApiKey;
    private String customModel;
    private String createdAt;
}
