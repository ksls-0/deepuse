package org.example.deepusecode.character.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationVO {
    private Long id;
    private Long templateId;
    private String templateName;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
