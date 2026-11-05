package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank(message = "消息不能为空")
    @Size(max = 4000, message = "单条消息不能超过 4000 个字符")
    private String message;
}
