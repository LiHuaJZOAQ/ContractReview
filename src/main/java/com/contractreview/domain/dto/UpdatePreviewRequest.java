package com.contractreview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePreviewRequest {
    @NotBlank
    @Size(max = 200000)
    private String text;
}
