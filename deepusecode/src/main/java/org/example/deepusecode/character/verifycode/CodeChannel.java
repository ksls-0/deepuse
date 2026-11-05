package org.example.deepusecode.character.verifycode;

/**
 * 验证码通道。邮箱与手机号各自独立，互不通用：
 * 用邮箱收到的验证码不能拿去完成手机号注册。
 */
public enum CodeChannel {

    EMAIL("邮箱"),
    PHONE("手机号");

    private final String label;

    CodeChannel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 用于 Redis key 的小写标识 */
    public String key() {
        return name().toLowerCase();
    }
}
