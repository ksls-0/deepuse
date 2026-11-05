package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailLoginRequest {

    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = AccountPatterns.EMAIL, message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码过长")
    private String password;
}
