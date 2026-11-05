package org.example.deepusecode.character.entity;

/**
 * 可见性取值。用 Integer 落库而不是 Boolean，
 * 一是给以后扩 "unlisted" 留位置，二是避开 Hibernate 对 is_ 前缀字段的映射坑。
 */
public final class Visibility {

    public static final int PRIVATE = 0;
    public static final int PUBLIC = 1;

    private Visibility() {
    }

    public static boolean isPublic(Integer value) {
        return value != null && value == PUBLIC;
    }

    public static Integer normalize(Integer value) {
        return value != null && value == PUBLIC ? PUBLIC : PRIVATE;
    }
}
