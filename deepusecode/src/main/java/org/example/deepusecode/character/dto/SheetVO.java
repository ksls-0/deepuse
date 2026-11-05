package org.example.deepusecode.character.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 一份答卷的完整内容（含题目文本，前端拿来自渲染填写页） */
@Data
@Builder
public class SheetVO {
    private Long id;
    private Long setId;
    private String setTitle;
    private String personaName;
    private Integer personaAge;
    private String personaOccupation;
    private String note;
    private String label;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Item> answers;

    @Data
    @Builder
    public static class Item {
        private Long questionId;
        private String question;
        private Integer sortOrder;
        private String content;
    }
}
