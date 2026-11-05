package org.example.deepusecode.character.security;

import lombok.extern.slf4j.Slf4j;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流（内存滑动窗口）。
 * <p>砍掉验证码后，账号密码登录是唯一的爆破入口，必须限流。
 * 单机内存实现足够本项目使用；多实例部署时应换成 Redis。</p>
 */
@Slf4j
@Component
public class LoginAttemptService {

    /** 窗口内允许的失败次数 */
    private static final int MAX_FAILURES = 5;
    /** 统计窗口 */
    private static final Duration WINDOW = Duration.ofMinutes(10);
    /** 防止 key 无限增长 */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    /** 登录前调用，超限直接拒绝 */
    public void checkBlocked(String key) {
        Deque<Instant> timestamps = failures.get(key);
        if (timestamps == null) {
            return;
        }
        synchronized (timestamps) {
            prune(timestamps);
            if (timestamps.size() >= MAX_FAILURES) {
                log.warn("登录失败次数超限，已临时拒绝: {}", key);
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "失败次数过多，请 10 分钟后再试");
            }
        }
    }

    /** 密码错误时调用 */
    public void onFailure(String key) {
        if (failures.size() > MAX_TRACKED_KEYS) {
            failures.clear();
        }
        Deque<Instant> timestamps = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            prune(timestamps);
            timestamps.addLast(Instant.now());
        }
    }

    /** 登录成功时调用，清掉历史失败 */
    public void onSuccess(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> timestamps) {
        Instant threshold = Instant.now().minus(WINDOW);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(threshold)) {
            timestamps.pollFirst();
        }
    }
}
