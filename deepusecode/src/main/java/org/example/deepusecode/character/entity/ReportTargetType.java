package org.example.deepusecode.character.entity;

/**
 * 举报目标类型。
 */
public final class ReportTargetType {

    public static final String SET = "SET";
    public static final String TEMPLATE = "TEMPLATE";

    private ReportTargetType() {
    }

    public static boolean isValid(String type) {
        return SET.equals(type) || TEMPLATE.equals(type);
    }
}
