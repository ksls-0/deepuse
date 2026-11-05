package org.example.deepusecode.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送验证码。target 的具体格式由接口路径决定
 * （/user/code/email 要求邮箱、/user/code/phone 要求手机号），
 * 控制器里按通道分别校验并给出对应提示。
 */
@Data
public class SendCodeRequest {

    @NotBlank(message = "请填写接收验证码的账号")
    @Size(max = 150, message = "账号过长")
    private String target;
}
