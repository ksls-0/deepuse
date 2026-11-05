package org.example.deepusecode.character.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateSetRequest {

    @NotBlank(message = "模板标题不能为空")
    @Size(max = 120, message = "标题不能超过 120 个字符")
    private String title;

    @Size(max = 500, message = "简介不能超过 500 个字符")
    private String description;

    /**
     * 增量同步而不是全量覆盖：
     * 带 id 的题目原地更新（question.id 不变，已有答卷不受影响），
     * 不带 id 的作为新增，未出现在列表里的视为删除。
     */
    @Valid
    private List<QuestionPayload> questions;
}
