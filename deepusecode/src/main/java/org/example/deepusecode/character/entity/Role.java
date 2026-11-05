package org.example.deepusecode.character.entity;

/**
 * 用户角色。
 * <p>之所以用字符串常量而不是 JPA enum + ORDINAL：
 * 字符串落库可读性好，也不会因为枚举顺序调整而把历史数据读错。</p>
 */
public final class Role {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";

    private Role() {
    }

    public static boolean isAdmin(String role) {
        return ADMIN.equals(role);
    }

    /** Spring Security 的 hasRole('ADMIN') 期待 ROLE_ 前缀 */
    public static String authority(String role) {
        return "ROLE_" + (ADMIN.equals(role) ? ADMIN : USER);
    }
}
