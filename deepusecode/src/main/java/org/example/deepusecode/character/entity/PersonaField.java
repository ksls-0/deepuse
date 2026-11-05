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
 * 一份答卷里用户自定义的一条「基础信息类别」。
 *
 * <p>内置字段只有昵称（在 {@link AnswerSheet#personaName} 上），
 * 其余像职业、年龄、兴趣、口头禅都由用户自己定义类别名和内容。</p>
 */
@Entity
@Table(name = "persona_field", indexes = {
        @Index(name = "idx_persona_sheet", columnList = "sheet_id,sort_order")
})
@Getter
@Setter
@NoArgsConstructor
public class PersonaField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sheet_id", nullable = false)
    private Long sheetId;

    /** 类别名，例如「职业」「年龄」「兴趣爱好」 */
    @Column(nullable = false, length = 50)
    private String label;

    /** 列名加 field_ 前缀：value 在 H2 是保留字，直接用会导致建表/查询语法错误 */
    @Column(name = "field_value", nullable = false, length = 500)
    private String value;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
