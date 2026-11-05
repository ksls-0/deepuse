package org.example.deepusecode.character.verifycode;

import lombok.extern.slf4j.Slf4j;
import org.example.deepusecode.character.common.AccountMasker;
import org.springframework.stereotype.Component;

/**
 * 邮箱验证码发送器（开发实现：只把验证码打到日志，不真正发信）。
 *
 * <p><b>接入真实邮件时这样改：</b></p>
 * <ol>
 *   <li>在 pom 里加 {@code spring-boot-starter-mail}</li>
 *   <li>配置 {@code spring.mail.*}（SMTP 主机、端口、账号、授权码）</li>
 *   <li>把下面 {@code send} 方法体换成 {@code JavaMailSender.send(...)}</li>
 * </ol>
 */
@Slf4j
@Component
public class EmailCodeSender implements CodeSender {

    @Override
    public CodeChannel channel() {
        return CodeChannel.EMAIL;
    }

    @Override
    public void send(String target, String code) {
        // 显式标注开发模式，避免误以为真的发出去了
        log.info("【邮箱验证码·开发模式未真实发送】收件人={}, 验证码={}",
                AccountMasker.mask(target), code);
    }
}
