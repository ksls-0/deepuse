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
 * 一次作答。同一个用户对同一个题集可以有多份答卷（不同答案版本），
 * 每份答卷可以各自生成自己的模板。
 */
@Entity
@Table(name = "answer_sheet", indexes = {
        @Index(name = "idx_sheet_set_user", columnList = "set_id,user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AnswerSheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 角色昵称——内置的基本信息只有这一项。
     * 其余信息（职业、年龄、兴趣…）由用户在 persona_field 里自定义类别。
     */
    @Column(name = "persona_name", length = 50)
    private String personaName;

    @Column(length = 1000)
    private String note;

    /** 用来在“我的答卷”里区分不同版本 */
    @Column(length = 60)
    private String label;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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
