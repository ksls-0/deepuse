package org.example.deepusecode.character.store;

import java.time.Duration;

/**
 * 带过期时间的键值 + 计数器存储抽象。
 *
 * <p>目前有两个使用场景，所以刻意做成通用的：</p>
 * <ul>
 *   <li>注册验证码（{@code verifycode} 包）——短 TTL 的字符串</li>
 *   <li>AI 生成配额（{@code QuotaService}）——按天计数</li>
 * </ul>
 *
 * <p>生产用 Redis（{@link RedisTtlStore}），集成测试用内存实现
 * （{@link InMemoryTtlStore}），所以 {@code mvn test} 不需要真实 Redis。</p>
 */
public interface TtlStore {

    void put(String key, String value, Duration ttl);

    String get(String key);

    void delete(String key);

    /**
     * 仅当 key 不存在时写入，用于「N 秒内只允许一次」这类限流。
     *
     * @return true 表示写入成功（即之前不存在或已过期）
     */
    boolean putIfAbsent(String key, String value, Duration ttl);

    /**
     * 计数器自增，首次自增时设置过期时间。
     *
     * @return 自增后的值
     */
    long increment(String key, Duration ttl);

    /**
     * 计数器自减，用于"先把额度扣掉、失败再退还"的场景。
     * 减到 0 以下时归零并删除 key。
     */
    void decrement(String key);

    /**
     * 读计数器当前值；key 不存在或已过期时返回 0。
     */
    long current(String key);
}
