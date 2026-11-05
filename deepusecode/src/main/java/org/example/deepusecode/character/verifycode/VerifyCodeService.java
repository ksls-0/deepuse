package org.example.deepusecode.character.verifycode;

import lombok.extern.slf4j.Slf4j;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 验证码服务：发送、限流、校验。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>验证码 6 位数字，有效期 5 分钟</li>
 *   <li>同一目标 60 秒内只能发一次</li>
 *   <li>同一目标每天最多发 10 次</li>
 *   <li>校验成功后立即作废，不能重复使用</li>
 * </ul>
 */
@Slf4j
@Service
public class VerifyCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final Duration DAILY_WINDOW = Duration.ofHours(24);
    private static final int DAILY_LIMIT = 10;

    private static final String CODE_KEY_PREFIX = "verify:code:";
    private static final String INTERVAL_KEY_PREFIX = "verify:interval:";
    private static final String DAILY_KEY_PREFIX = "verify:daily:";

    private final SecureRandom random = new SecureRandom();
    private final CodeStore codeStore;
    private final Map<CodeChannel, CodeSender> senders = new EnumMap<>(CodeChannel.class);

    public VerifyCodeService(CodeStore codeStore, List<CodeSender> codeSenders) {
        this.codeStore = codeStore;
        for (CodeSender sender : codeSenders) {
            senders.put(sender.channel(), sender);
        }
        if (senders.size() < CodeChannel.values().length) {
            log.warn("有验证码通道没有对应发送器，可用通道: {}", senders.keySet());
        }
    }

    /** Redis key 规则，公开出来供测试读取验证码用 */
    public static String codeKey(String target, CodeChannel channel) {
        return CODE_KEY_PREFIX + channel.key() + ":" + target;
    }

    /**
     * 发送验证码。
     *
     * @throws BusinessException 发送过于频繁 / 超过每日上限 / 通道不可用
     */
    public void sendCode(String target, CodeChannel channel) {
        CodeSender sender = senders.get(channel);
        if (sender == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, channel.label() + "验证码通道未配置");
        }

        // 1) 60 秒内只允许发一次
        String intervalKey = INTERVAL_KEY_PREFIX + channel.key() + ":" + target;
        if (!codeStore.putIfAbsent(intervalKey, "1", RESEND_INTERVAL)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "验证码发送过于频繁，请 60 秒后再试");
        }

        // 2) 每日上限，防止被刷短信费用
        String dailyKey = DAILY_KEY_PREFIX + channel.key() + ":" + target;
        long sent = codeStore.increment(dailyKey, DAILY_WINDOW);
        if (sent > DAILY_LIMIT) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "今日验证码发送次数已达上限（" + DAILY_LIMIT + " 次），请明天再试");
        }

        // 3) 生成并存储
        String code = String.format("%06d", random.nextInt(1_000_000));
        codeStore.put(codeKey(target, channel), code, CODE_TTL);

        // 4) 交给通道发送（当前是打日志实现）
        sender.send(target, code);

        log.info("验证码已生成: channel={}, target={}, 当日第 {} 次", channel, maskForLog(target), sent);
    }

    /**
     * 校验验证码，成功即作废。
     *
     * @throws BusinessException 验证码不存在 / 过期 / 不匹配
     */
    public void verifyCode(String target, CodeChannel channel, String input) {
        if (input == null || input.isBlank()) {
            throw BusinessException.badRequest("请填写验证码");
        }
        String key = codeKey(target, channel);
        String saved = codeStore.get(key);
        if (saved == null) {
            throw BusinessException.badRequest("验证码已过期，请重新获取");
        }
        if (!saved.equals(input.trim())) {
            throw BusinessException.badRequest("验证码不正确");
        }
        // 一次性：校验通过立即删除，避免同一验证码重复注册
        codeStore.delete(key);
    }

    private String maskForLog(String target) {
        if (target == null || target.length() < 6) {
            return "***";
        }
        return target.substring(0, 3) + "***";
    }
}
