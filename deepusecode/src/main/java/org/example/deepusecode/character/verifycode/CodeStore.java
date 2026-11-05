package org.example.deepusecode.character.verifycode;

import java.time.Duration;

/**
 * 验证码存储抽象。
 * <p>生产用 Redis（见 {@link RedisCodeStore}），测试用内存实现
 * （见 {@link InMemoryCodeStore}），这样集成测试不需要跑一个真实 Redis。</p>
 */
public interface CodeStore {

    void put(String key, String value, Duration ttl);

    String get(String key);

    void delete(String key);

    /**
     * 仅当 key 不存在时写入，用于「60 秒内只允许发一次」这类限流。
     *
     * @return true 表示写入成功（即之前不存在）
     */
    boolean putIfAbsent(String key, String value, Duration ttl);

    /**
     * 计数器自增，首次自增时设置过期时间。用于每日发送次数上限。
     *
     * @return 自增后的值
     */
    long increment(String key, Duration ttl);
}
