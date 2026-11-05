package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateConversationRequest {

    @NotNull(message = "模板 id 不能为空")
    private Long templateId;
}
