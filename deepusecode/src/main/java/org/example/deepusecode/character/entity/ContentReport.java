package org.example.deepusecode.character.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户对公开内容的举报。
 * <p>同一个人对同一内容只能举报一次（唯一索引兜底），避免刷举报淹没审核队列。</p>
 */
@Entity
@Table(name = "content_report",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_report_once",
                columnNames = {"reporter_id", "target_type", "target_id"}),
        indexes = {
                @Index(name = "idx_report_status", columnList = "status,created_at"),
                @Index(name = "idx_report_target", columnList = "target_type,target_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class ContentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 违规分类，例如 违法违规 / 色情低俗 / 侵权 / 广告骚扰 / 其他 */
    @Column(nullable = false, length = 50)
    private String reason;

    @Column(length = 500)
    private String detail;

    @Column(nullable = false, length = 20)
    private String status = ReportStatus.PENDING;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "handle_note", length = 200)
    private String handleNote;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
