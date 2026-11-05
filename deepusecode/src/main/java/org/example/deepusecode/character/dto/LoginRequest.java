package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求。密码走请求体，不再拼在 URL 上（旧的 GET 写法会把明文密码
 * 写进 access log、浏览器历史和代理日志）。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = AccountPatterns.ACCOUNT, message = "账号需为手机号或邮箱")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码过长")
    private String password;
}
