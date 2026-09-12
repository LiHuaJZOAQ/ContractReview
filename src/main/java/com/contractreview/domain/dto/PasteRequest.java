package com.contractreview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasteRequest {
    @NotBlank(message = "合同文本不能为空")
    @Size(max = 500000, message = "合同文本不能超过50万字符")
    private String text;
}
