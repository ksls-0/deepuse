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
 * 题集：用户创作的一组问题，是可复用、可公开、可被他人导入的资产。
 */
@Entity
@Table(name = "question_set", indexes = {
        @Index(name = "idx_set_owner", columnList = "owner_id,deleted"),
        @Index(name = "idx_set_public", columnList = "visibility,deleted,created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class QuestionSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Integer visibility = Visibility.PRIVATE;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount = 0;

    @Column(name = "answer_count", nullable = false)
    private Integer answerCount = 0;

    @Column(name = "import_count", nullable = false)
    private Integer importCount = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Integer deleted = 0;

    /**
     * 是否被管理员强制下架。与 visibility 正交：
     * visibility 是作者的意愿，taken_down 是平台的处置，后者优先。
     */
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
