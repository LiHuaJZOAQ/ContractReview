package com.contractreview.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {
    @Size(min = 2, max = 50, message = "用户名长度需在2-50之间")
    private String username;
}
