package org.example.deepusecode.character.entity;

/**
 * 模板生成状态。0 预留给以后改成异步生成时使用。
 */
public final class GenStatus {

    public static final int PENDING = 0;
    public static final int SUCCESS = 1;
    public static final int FAILED = 2;

    private GenStatus() {
    }
}
