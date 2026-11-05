package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VisibilityRequest {

    /** 0 私密 / 1 公开 */
    @NotNull(message = "可见性不能为空")
    @Min(value = 0, message = "可见性取值只能是 0 或 1")
    @Max(value = 1, message = "可见性取值只能是 0 或 1")
    private Integer visibility;
}
