package org.example.deepusecode.character.store;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的实现（生产）。
 * <p>多实例部署时各节点共享验证码与配额，TTL 由 Redis 自己管，不用写清理任务。</p>
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisTtlStore implements TtlStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void put(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public boolean putIfAbsent(String key, String value, Duration ttl) {
        Boolean written = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(written);
    }

    @Override
    public long increment(String key, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            // 只在首次自增时设置过期，避免每次自增都续期导致计数永不过期
            redisTemplate.expire(key, ttl);
        }
        return value == null ? 0L : value;
    }

    @Override
    public void decrement(String key) {
        Long value = redisTemplate.opsForValue().decrement(key);
        if (value == null || value <= 0L) {
            redisTemplate.delete(key);
        }
    }

    @Override
    public long current(String key) {
        String raw = redisTemplate.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
