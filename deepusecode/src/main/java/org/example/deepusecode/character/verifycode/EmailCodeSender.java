package org.example.deepusecode.character.verifycode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deepusecode.character.common.AccountMasker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 邮箱验证码发送器。
 *
 * <p>自适应两种模式：</p>
 * <ul>
 *   <li>配了 {@code spring.mail.host} → Spring Boot 会注册 {@link JavaMailSender}，真正发信</li>
 *   <li>没配 → 退化为把验证码打到日志，本地开发不需要邮箱账号</li>
 * </ul>
 *
 * <p>之所以用 {@link ObjectProvider} 而不是直接注入：没配 SMTP 时容器里根本没有这个 bean，
 * 直接注入会导致启动失败。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailCodeSender implements CodeSender {

    /** 验证码有效期，与 VerifyCodeService 保持一致 */
    private static final int CODE_TTL_MINUTES = 5;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /** 发件人展示地址；不填则退回用 SMTP 登录账号 */
    @Value("${app.mail.from:}")
    private String fromOverride;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Override
    public CodeChannel channel() {
        return CodeChannel.EMAIL;
    }

    @Override
    public void send(String target, String code) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            // 没配 SMTP：打日志，本地开发照常跑
            log.warn("【邮箱验证码·未配置 SMTP，未真实发送】收件人={}, 验证码={}",
                    AccountMasker.mask(target), code);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(resolveFrom());
        message.setTo(target);
        message.setSubject("【角色工坊】邮箱验证码 " + code);
        message.setText(buildBody(code));

        // 发送失败直接抛出去，由 VerifyCodeService 撤销验证码并提示用户，
        // 不要让用户干等一个根本发不出去的验证码
        mailSender.send(message);
        log.info("邮箱验证码已发送: {}", AccountMasker.mask(target));
    }

    private String resolveFrom() {
        if (fromOverride != null && !fromOverride.isBlank()) {
            return fromOverride.trim();
        }
        return smtpUsername;
    }

    private String buildBody(String code) {
        return """
                你正在注册「角色工坊」账号，验证码是：

                    %s

                该验证码 %d 分钟内有效，且只能使用一次。
                如果不是你本人操作，请忽略这封邮件。
                """.formatted(code, CODE_TTL_MINUTES);
    }
}
