package org.example.deepusecode.character.service;

import lombok.extern.slf4j.Slf4j;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.common.ErrorCode;
import org.example.deepusecode.character.store.TtlStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * AI 生成配额。
 *
 * <p>每次成功生成角色消耗 1 次额度，按用户 24 小时滚动窗口计数。
 * 存在的意义很直接：不设上限的话，一个用户狂点「生成」就能把 DeepSeek 账单刷爆。</p>
 *
 * <p>策略是「先扣额度，失败退还」——因为 AI 调用本身就会产生费用，
 * 只统计成功次数的话，反复失败的重试也保护不到。而失败退还又不会冤枉用户。</p>
 */
@Slf4j
@Service
public class QuotaService {

    private static final Duration WINDOW = Duration.ofHours(24);
    private static final String KEY_PREFIX = "ai:quota:gen:";

    private final TtlStore ttlStore;
    private final int dailyGenerationLimit;

    public QuotaService(TtlStore ttlStore,
                        @Value("${app.ai.daily-generation-limit:20}") int dailyGenerationLimit) {
        this.ttlStore = ttlStore;
        this.dailyGenerationLimit = dailyGenerationLimit;
    }

    /**
     * 扣一次生成额度。超额时抛 429。
     *
     * @throws BusinessException 额度用尽
     */
    public void consumeGeneration(Long userId) {
        if (dailyGenerationLimit <= 0) {
            // 配成 0 或负数表示不限量，方便本地压测
            return;
        }
        String key = key(userId);
        long used = ttlStore.increment(key, WINDOW);
        if (used > dailyGenerationLimit) {
            // 被拒绝的这次不该占用额度，退回去
            ttlStore.decrement(key);
            log.info("生成配额用尽: userId={}, 已用={}, 上限={}", userId, used - 1, dailyGenerationLimit);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "今日 AI 生成次数已用完（上限 " + dailyGenerationLimit + " 次），请明天再试");
        }
        log.debug("生成配额: userId={}, 已用 {}/{}", userId, used, dailyGenerationLimit);
    }

    /**
     * 生成失败时退还额度，避免 AI 异常把用户的额度白白吃掉。
     */
    public void refundGeneration(Long userId) {
        if (dailyGenerationLimit <= 0) {
            return;
        }
        ttlStore.decrement(key(userId));
    }

    /** 今日剩余次数，供接口展示 */
    public int remaining(Long userId) {
        if (dailyGenerationLimit <= 0) {
            return Integer.MAX_VALUE;
        }
        long used = ttlStore.current(key(userId));
        return (int) Math.max(0, dailyGenerationLimit - used);
    }

    public int dailyGenerationLimit() {
        return dailyGenerationLimit;
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
