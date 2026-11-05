package org.example.deepusecode.character.dto;

import lombok.Data;

/** 生成角色的入参：指定用哪份答卷生成（不传则用最新一份） */
@Data
public class GenerateTemplateRequest {
    private Long sheetId;
}
