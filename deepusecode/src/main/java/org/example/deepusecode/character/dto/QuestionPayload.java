package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 题集里的一个问题。更新题集时带上 id 表示"保留并修改这一题"，
 * 不带 id 表示新增；服务端按 id 做 diff，从而保住 question.id 不变，
 * 不会让已保存答卷里的 question_id 失效。
 */
@Data
public class QuestionPayload {

    /** 新增时为空 */
    private Long id;

    @NotBlank(message = "问题内容不能为空")
    @Size(max = 500, message = "单个问题不能超过 500 个字符")
    private String content;
}
