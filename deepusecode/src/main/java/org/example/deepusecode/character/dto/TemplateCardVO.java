package org.example.deepusecode.character.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TemplateCardVO {
    private Long id;
    private String name;
    /** 一句话人设，从 rawJson.tagline 提取，提取不到给空串 */
    private String tagline;
    private Integer visibility;
    private String ownerAccount;
    private Integer useCount;
    private Long setId;
    private String setTitle;
    private LocalDateTime createdAt;
    private boolean mine;
}
