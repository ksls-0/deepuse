package org.example.deepusecode.character.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 审核队列里的一条举报（已带上被举报内容的摘要，避免管理端反复查） */
@Data
@Builder
public class ReportVO {
    private Long id;
    private String targetType;
    private Long targetId;
    /** 被举报内容的标题 / 名字；内容已被删除时为提示文案 */
    private String targetTitle;
    private Long targetOwnerId;
    private String targetOwnerAccount;
    /** 被举报内容当前是否处于下架状态 */
    private boolean targetTakenDown;
    private Long reporterId;
    private String reporterAccount;
    private String reason;
    private String detail;
    private String status;
    private Long handledBy;
    private LocalDateTime handledAt;
    private String handleNote;
    private LocalDateTime createdAt;
}
