package org.example.deepusecode.character.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 一轮问答的返回：用户消息 + AI 回复，前端可直接追加到列表 */
@Data
@AllArgsConstructor
public class SendMessageResponse {
    private MessageVO userMessage;
    private MessageVO assistantMessage;
}
