package org.example.deepusecode.character.dto;

/**
 * 账号格式：11 位手机号，或常规邮箱。
 */
public final class AccountPatterns {

    public static final String ACCOUNT =
            "^(1[3-9]\\d{9}|[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})$";

    private AccountPatterns() {
    }
}
