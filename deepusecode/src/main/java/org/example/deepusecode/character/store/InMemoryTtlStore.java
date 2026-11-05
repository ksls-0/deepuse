package org.example.deepusecode.character.store;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存实现，仅供集成测试使用。
 * <p>有了它，`mvn test` 不需要依赖真实的 Redis 实例。</p>
 */
@Component
@Profile("test")
public class InMemoryTtlStore implements TtlStore {

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
        Entry fresh = new Entry(value, Instant.now().plus(ttl));
        Entry existing = store.compute(key, (k, current) ->
                (current == null || current.expired()) ? fresh : current);
        return existing == fresh;
    }

    @Override
    public long increment(String key, Duration ttl) {
        resetIfExpired(key, ttl);
        return counter(key).incrementAndGet();
    }

    @Override
    public void decrement(String key) {
        AtomicLong counter = counters.get(key);
        if (counter == null) {
            return;
        }
        if (counter.decrementAndGet() <= 0) {
            counters.remove(key);
            counterExpiry.remove(key);
        }
    }

    @Override
    public long current(String key) {
        Instant expireAt = counterExpiry.get(key);
        if (expireAt != null && Instant.now().isAfter(expireAt)) {
            counters.remove(key);
            counterExpiry.remove(key);
            return 0L;
        }
        AtomicLong counter = counters.get(key);
        return counter == null ? 0L : counter.get();
    }

    private AtomicLong counter(String key) {
        return counters.computeIfAbsent(key, k -> new AtomicLong());
    }

    private void resetIfExpired(String key, Duration ttl) {
        Instant expireAt = counterExpiry.get(key);
        if (expireAt == null) {
            counterExpiry.put(key, Instant.now().plus(ttl));
            return;
        }
        if (Instant.now().isAfter(expireAt)) {
            counters.remove(key);
            counterExpiry.put(key, Instant.now().plus(ttl));
        }
    }
}
