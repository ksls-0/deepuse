package org.example.deepusecode.character.common;

import java.util.regex.Pattern;

/**
 * 账号脱敏。广场等公开位置不应出现完整的手机号 / 邮箱。
 */
public final class AccountMasker {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    private AccountMasker() {
    }

    /**
     * 13812345678 -> 138****5678
     * abc@x.com   -> ab***@x.com
     * 其他         -> 前两位 + ***
     */
    public static String mask(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        String value = account.trim();

        if (PHONE.matcher(value).matches()) {
            return value.substring(0, 3) + "****" + value.substring(7);
        }

        int at = value.indexOf('@');
        if (at > 0) {
            String name = value.substring(0, at);
            String domain = value.substring(at);
            String head = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
            return head + "***" + domain;
        }

        if (value.length() <= 2) {
            return value.charAt(0) + "***";
        }
        return value.substring(0, 2) + "***";
    }

    /**
     * 自己的内容返回原文，别人的内容脱敏。
     */
    public static String maskForViewer(String account, Long viewerId, Long ownerId) {
        if (viewerId != null && viewerId.equals(ownerId)) {
            return account;
        }
        return mask(account);
    }
}
