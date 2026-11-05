package org.example.deepusecode.character.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 题集下的单个问题，按 sortOrder 升序展示。
 */
@Entity
@Table(name = "question", indexes = {
        @Index(name = "idx_question_set", columnList = "set_id,sort_order")
})
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
