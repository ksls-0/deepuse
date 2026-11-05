package org.example.deepusecode.character.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionVO {
    private Long id;
    private String content;
    private Integer sortOrder;
}
