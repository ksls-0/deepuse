package org.example.deepusecode.character.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 答卷中的单题答案。
 */
@Entity
@Table(name = "answer",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_answer_sheet_question", columnNames = {"sheet_id", "question_id"}),
        indexes = @Index(name = "idx_answer_sheet", columnList = "sheet_id"))
@Getter
@Setter
@NoArgsConstructor
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sheet_id", nullable = false)
    private Long sheetId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(length = 2000)
    private String content;
}
