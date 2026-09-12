package com.contractreview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LawRequest {
    @NotBlank(message = "法律名称不能为空")
    @Size(max = 200, message = "法律名称不能超过200字符")
    private String title;

    @Size(max = 50, message = "分类名称不能超过50字符")
    private String category;

    @NotBlank(message = "法律内容不能为空")
    @Size(max = 500000, message = "法律内容不能超过50万字符")
    private String content;
}
