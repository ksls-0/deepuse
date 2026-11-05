package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PhoneRegisterRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = AccountPatterns.PHONE, message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码需长在 6~64 位之间")
    private String password;
}
