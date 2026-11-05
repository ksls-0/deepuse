package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理员处理举报 */
@Data
public class HandleReportRequest {

    /** TAKEDOWN（举报成立，下架内容）/ REJECT（举报不成立） */
    @NotBlank(message = "处理动作不能为空")
    private String action;

    @Size(max = 200, message = "处理备注不能超过 200 个字符")
    private String note;
}
