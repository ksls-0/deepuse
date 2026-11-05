package org.example.deepusecode.character.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SetDetailVO {
    private Long id;
    private String title;
    private String description;
    private Integer visibility;
    private Integer questionCount;
    private Integer answerCount;
    private Integer importCount;
    private Long ownerId;
    private String ownerAccount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean mine;
    private List<QuestionVO> questions;
}
