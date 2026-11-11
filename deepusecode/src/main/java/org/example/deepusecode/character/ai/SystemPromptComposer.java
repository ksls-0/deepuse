package org.example.deepusecode.character.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 把结构化角色卡（{@code ai_template.raw_json}）拼成可以直接当 system message 用的文本。
 *
 * <p>存在的理由：早期（还在 mock 阶段）角色的 systemPrompt 是本地 {@code ## 身份 / ## 扮演要求}
 * 那种拼接格式，字段里还带着「年龄 / 职业」这类已废弃的固定项；对话时把它原样注入，
 * 看起来就像"退回了旧的数据格式"。这里给出一份唯一的、由卡片字段驱动的拼装逻辑，
 * mock 兜底和存量数据修复都走它，保证注入文本只有一种结构。</p>
 *
 * <p>真实 AI 生成的角色**不**走这里——模型自己写的 systemPrompt（300~600 字、第二人称指令）
 * 质量明显更好，保留它。</p>
 */
@Component
public class SystemPromptComposer {

    /**
     * 由卡片字段拼装 system prompt。字段缺失就跳过对应小节，不会输出空标题。
     *
     * @param card     解析后的角色卡，必须是非空对象
     * @param fallback 卡片里完全取不到可用信息时的兜底文本（通常是原 content）
     */
    public String compose(JsonNode card, String fallback) {
        if (card == null || !card.isObject()) {
            return fallback;
        }

        String name = text(card, "name");
        String tagline = text(card, "tagline");
        String background = text(card, "background");
        String speakingStyle = text(card, "speakingStyle");
        List<String> personality = list(card, "personality");
        List<String> catchphrases = list(card, "catchphrases");
        List<String> taboos = list(card, "taboos");

        if (name.isEmpty() && background.isEmpty() && personality.isEmpty()) {
            return fallback;
        }
        if (name.isEmpty()) {
            name = "这个角色";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("你现在要扮演「").append(name).append("」。");
        if (!tagline.isEmpty()) {
            sb.append(tagline);
        }
        sb.append("\n\n");

        appendList(sb, "## 性格", personality);
        appendBlock(sb, "## 背景", background);
        appendBlock(sb, "## 说话方式", speakingStyle);
        appendList(sb, "## 口头禅", catchphrases);
        appendDialogue(sb, card.path("exampleDialogue"), name);
        appendList(sb, "## 绝对不做", taboos);

        sb.append("## 扮演要求\n");
        sb.append("- 始终以第一人称、口语化的方式回应，只输出角色自己的台词，不要旁白或解释。\n");
        sb.append("- 严格贴合上面的性格与说话方式，不要跳出角色，也不要复述这些设定。");
        return sb.toString();
    }

    private void appendList(StringBuilder sb, String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        sb.append(title).append('\n');
        for (String item : items) {
            sb.append("- ").append(item).append('\n');
        }
        sb.append('\n');
    }

    private void appendBlock(StringBuilder sb, String title, String body) {
        if (body.isEmpty()) {
            return;
        }
        sb.append(title).append('\n').append(body).append("\n\n");
    }

    private void appendDialogue(StringBuilder sb, JsonNode dialogue, String name) {
        if (dialogue == null || !dialogue.isArray() || dialogue.isEmpty()) {
            return;
        }
        StringBuilder body = new StringBuilder();
        for (JsonNode turn : dialogue) {
            String user = turn.path("user").asText("").trim();
            String assistant = turn.path("assistant").asText("").trim();
            if (user.isEmpty() || assistant.isEmpty()) {
                continue;
            }
            body.append("用户：").append(user).append('\n')
                    .append(name).append("：").append(assistant).append('\n');
        }
        if (body.isEmpty()) {
            return;
        }
        sb.append("## 示例对话\n").append(body).append('\n');
    }

    private String text(JsonNode card, String field) {
        JsonNode node = card.path(field);
        return node.isTextual() ? node.asText().trim() : "";
    }

    private List<String> list(JsonNode card, String field) {
        JsonNode node = card.path(field);
        List<String> result = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return result;
        }
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                result.add(item.asText().trim());
            }
        }
        return result;
    }
}
