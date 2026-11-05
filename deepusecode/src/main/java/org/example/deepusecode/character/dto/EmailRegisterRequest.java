package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailRegisterRequest {

    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = AccountPatterns.EMAIL, message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6~64 位之间")
    private String password;
}
