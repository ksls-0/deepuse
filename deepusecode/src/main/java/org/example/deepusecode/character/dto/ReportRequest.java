package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 用户提交举报 */
@Data
public class ReportRequest {

    /** SET / TEMPLATE */
    @NotBlank(message = "举报对象类型不能为空")
    private String targetType;

    @NotNull(message = "举报对象 id 不能为空")
    private Long targetId;

    /** 违规分类 */
    @NotBlank(message = "请选择举报原因")
    @Size(max = 50, message = "举报原因过长")
    private String reason;

    @Size(max = 500, message = "补充说明不能超过 500 个字符")
    private String detail;
}
