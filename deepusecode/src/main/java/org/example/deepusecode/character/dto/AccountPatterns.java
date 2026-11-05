package org.example.deepusecode.character.dto;

/**
 * 账号格式常量。
 * <ul>
 *   <li>{@link #PHONE} —— 11 位中国大陆手机号</li>
 *   <li>{@link #EMAIL} —— 常规邮箱</li>
 *   <li>{@link #ACCOUNT} —— 手机号或邮箱（保留给需要"两者皆可"的场景）</li>
 * </ul>
 */
public final class AccountPatterns {

    public static final String PHONE = "^1[3-9]\\d{9}$";

    public static final String EMAIL = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    public static final String ACCOUNT =
            "^(1[3-9]\\d{9}|[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})$";

    private AccountPatterns() {
    }
}
