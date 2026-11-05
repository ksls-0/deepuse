package org.example.deepusecode.character.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 容错解析模型返回的 JSON。
 * <p>模型偶尔会把 JSON 包在 ```json 围栏里，或在前后多写一句解释，
 * 所以按「直接解析 → 剥围栏 → 截取首尾大括号」三级降级尝试。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardJsonParser {

    private final ObjectMapper objectMapper;

    public JsonNode parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        JsonNode direct = tryRead(text);
        if (direct != null) {
            return direct;
        }

        String stripped = stripCodeFence(text);
        JsonNode afterFence = tryRead(stripped);
        if (afterFence != null) {
            return afterFence;
        }

        int start = stripped.indexOf('{');
        int end = stripped.lastIndexOf('}');
        if (start >= 0 && end > start) {
            JsonNode sliced = tryRead(stripped.substring(start, end + 1));
            if (sliced != null) {
                return sliced;
            }
        }

        log.warn("角色卡 JSON 解析失败，将按纯文本处理。原文前 200 字: {}",
                text.length() > 200 ? text.substring(0, 200) : text);
        return null;
    }

    private JsonNode tryRead(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }
        String body = trimmed.substring(firstNewline + 1);
        int fenceEnd = body.lastIndexOf("```");
        if (fenceEnd >= 0) {
            body = body.substring(0, fenceEnd);
        }
        return body.trim();
    }
}
