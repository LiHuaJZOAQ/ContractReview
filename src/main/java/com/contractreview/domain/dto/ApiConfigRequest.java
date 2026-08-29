package com.contractreview.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiConfigRequest {
    @Size(max = 512, message = "API地址过长")
    private String apiUrl;

    @Size(max = 512, message = "API Key过长")
    private String apiKey;

    @Size(max = 100, message = "模型名称过长")
    private String model;
}
