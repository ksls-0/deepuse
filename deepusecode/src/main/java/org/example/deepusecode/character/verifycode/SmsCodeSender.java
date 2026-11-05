package org.example.deepusecode.character.verifycode;

import lombok.extern.slf4j.Slf4j;
import org.example.deepusecode.character.common.AccountMasker;
import org.springframework.stereotype.Component;

/**
 * 手机验证码发送器（开发实现：只把验证码打到日志，不真正发短信）。
 *
 * <p><b>接入阿里云 / 腾讯云短信时这样改：</b></p>
 * <ol>
 *   <li>引入对应 SDK（阿里云 dysmsapi / 腾讯云 sms）</li>
 *   <li>把 AccessKey、签名、模板编号放到环境变量</li>
 *   <li>把下面 {@code send} 方法体换成 SDK 调用</li>
 * </ol>
 * 注意：短信签名与模板需要先通过运营商审核，通常 1~2 天。
 */
@Slf4j
@Component
public class SmsCodeSender implements CodeSender {

    @Override
    public CodeChannel channel() {
        return CodeChannel.PHONE;
    }

    @Override
    public void send(String target, String code) {
        log.info("【短信验证码·开发模式未真实发送】手机号={}, 验证码={}",
                AccountMasker.mask(target), code);
    }
}
