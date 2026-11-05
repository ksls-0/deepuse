package org.example.deepusecode.character.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模板详情。structured 是解析后的角色卡 JSON（name/personality/exampleDialogue...），
 * 前端直接按对象渲染；content 是最终给模型用的 system prompt。
 */
@Data
@Builder
public class TemplateVO {
    private Long id;
    private String name;
    private String content;
    private Object structured;
    private Integer visibility;
    private Integer genStatus;
    private String genModel;
    private Integer useCount;
    private Long setId;
    private String setTitle;
    private Long ownerId;
    private String ownerAccount;
    private LocalDateTime createdAt;
    private boolean mine;
}
