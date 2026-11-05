package org.example.deepusecode.character.entity;

/**
 * 举报状态。
 */
public final class ReportStatus {

    /** 待处理 */
    public static final String PENDING = "PENDING";
    /** 举报成立，内容已下架 */
    public static final String TAKEN_DOWN = "TAKEN_DOWN";
    /** 举报不成立 */
    public static final String REJECTED = "REJECTED";

    private ReportStatus() {
    }

    public static boolean isValid(String status) {
        return PENDING.equals(status) || TAKEN_DOWN.equals(status) || REJECTED.equals(status);
    }
}
