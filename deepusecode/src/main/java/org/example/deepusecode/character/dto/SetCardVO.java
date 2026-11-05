package org.example.deepusecode.character.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 列表卡片用的精简视图 */
@Data
@Builder
public class SetCardVO {
    private Long id;
    private String title;
    private String description;
    private Integer visibility;
    private Integer questionCount;
    private Integer answerCount;
    private Integer importCount;
    private String ownerAccount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean mine;
}
