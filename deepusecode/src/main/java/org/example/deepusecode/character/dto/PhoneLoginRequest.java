package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PhoneLoginRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = AccountPatterns.PHONE, message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码过长")
    private String password;
}
