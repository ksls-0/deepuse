package org.example.deepusecode.character.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateSetRequest {

    @NotBlank(message = "模板标题不能为空")
    @Size(max = 120, message = "标题不能超过 120 个字符")
    private String title;

    @Size(max = 500, message = "简介不能超过 500 个字符")
    private String description;

    /** 新建时 id 会被忽略 */
    @Valid
    private List<QuestionPayload> questions;
}
