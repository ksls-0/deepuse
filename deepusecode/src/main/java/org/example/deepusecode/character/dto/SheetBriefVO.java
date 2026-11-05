package org.example.deepusecode.character.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 我的答卷列表项 */
@Data
@Builder
public class SheetBriefVO {
    private Long id;
    private String label;
    private String personaName;
    private LocalDateTime updatedAt;
    private long answeredCount;
}
