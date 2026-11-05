package org.example.deepusecode.character.entity;

/**
 * 内容处置状态。
 *
 * <p>与 {@link Visibility} 正交：</p>
 * <ul>
 *   <li>{@code visibility} —— 作者自己的意愿（私密 / 公开）</li>
 *   <li>{@code takenDown} —— 平台的强制处置</li>
 * </ul>
 * 只要被下架，无论作者把 visibility 切成什么，内容都不出现在广场。
 */
public final class Moderation {

    public static final int NOT_TAKEN_DOWN = 0;
    public static final int TAKEN_DOWN = 1;

    private Moderation() {
    }

    public static boolean isTakenDown(Integer value) {
        return value != null && value == TAKEN_DOWN;
    }
}
