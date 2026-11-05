package org.example.deepusecode.character.verifycode;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存版验证码存储，仅供集成测试使用。
 * <p>有了它，`mvn test` 不需要依赖真实的 Redis 实例。</p>
 */
@Component
@Profile("test")
public class InMemoryCodeStore implements CodeStore {

    private record Entry(String value, Instant expireAt) {
        boolean expired() {
            return Instant.now().isAfter(expireAt);
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, Instant> counterExpiry = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value, Duration ttl) {
        store.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    @Override
    public String get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expired()) {
            store.remove(key);
            return null;
        }
        return entry.value();
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public boolean putIfAbsent(String key, String value, Duration ttl) {
        Instant now = Instant.now();
        Entry fresh = new Entry(value, now.plus(ttl));
        Entry existing = store.compute(key, (k, current) ->
                (current == null || current.expired()) ? fresh : current);
        return existing == fresh;
    }

    @Override
    public long increment(String key, Duration ttl) {
        Instant expireAt = counterExpiry.computeIfAbsent(key, k -> Instant.now().plus(ttl));
        if (Instant.now().isAfter(expireAt)) {
            // 超过窗口就重置
            counters.remove(key);
            counterExpiry.put(key, Instant.now().plus(ttl));
        }
        return counters.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }
}
