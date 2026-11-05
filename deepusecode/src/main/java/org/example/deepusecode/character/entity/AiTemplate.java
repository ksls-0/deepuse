package org.example.deepusecode.character.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 生成的角色模板。
 * <p>content 是拼好的 system prompt（对话时直接注入）；
 * rawJson 是结构化角色卡（前端渲染姓名、性格标签、示例对话等）。</p>
 */
@Entity
@Table(name = "ai_template", indexes = {
        @Index(name = "idx_tpl_owner", columnList = "owner_id,deleted"),
        @Index(name = "idx_tpl_public", columnList = "visibility,deleted,created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class AiTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "set_id")
    private Long setId;

    @Column(name = "sheet_id")
    private Long sheetId;

    @Column(nullable = false, length = 80)
    private String name;

    /** MEDIUMTEXT：避免 Hibernate 对 JSON 列的映射坑，解析交给 Jackson */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "raw_json", columnDefinition = "MEDIUMTEXT")
    private String rawJson;

    @Column(nullable = false)
    private Integer visibility = Visibility.PRIVATE;

    @Column(name = "gen_status", nullable = false)
    private Integer genStatus = GenStatus.SUCCESS;

    @Column(name = "gen_error", length = 500)
    private String genError;

    @Column(name = "gen_model", length = 60)
    private String genModel;

    @Column(name = "use_count", nullable = false)
    private Integer useCount = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Integer deleted = 0;

    /** 是否被管理员强制下架，与 visibility 正交，后者优先 */
    @Column(name = "taken_down", nullable = false)
    private Integer takenDown = 0;

    @Column(name = "takedown_reason", length = 200)
    private String takedownReason;

    @Column(name = "taken_down_at")
    private LocalDateTime takenDownAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
