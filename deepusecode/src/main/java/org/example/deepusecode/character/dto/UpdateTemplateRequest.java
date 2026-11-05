package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTemplateRequest {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 80, message = "角色名称不能超过 80 个字符")
    private String name;

    /** 允许用户微调 system prompt */
    @NotBlank(message = "角色内容不能为空")
    private String content;
}
