package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理员直接下架内容（不必先有举报） */
@Data
public class TakedownRequest {

    @NotBlank(message = "下架原因不能为空")
    @Size(max = 200, message = "原因不能超过 200 个字符")
    private String reason;
}
