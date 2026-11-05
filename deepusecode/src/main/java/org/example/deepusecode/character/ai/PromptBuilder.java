package org.example.deepusecode.character.ai;

import org.springframework.stereotype.Component;

/**
 * 构造喂给模型的提示词。
 * 生成提示词里必须出现 "JSON" 字样 —— DeepSeek 的 json_object 模式有此要求。
 */
@Component
public class PromptBuilder {

    private static final int MAX_ANSWER_LEN = 800;

    public String buildCharacterPrompt(CharacterPromptContext ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是一位资深的 AI 角色设定师。下面是一位用户为某个「角色题集」填写的问答，")
                .append("请据此创作一个可以直接驱动大模型扮演的角色设定。\n\n");

        sb.append("【题集】");
        sb.append(nullSafe(ctx.setName(), "未命名题集"));
        if (hasText(ctx.setDescription())) {
            sb.append(" —— ").append(ctx.setDescription().trim());
        }
        sb.append('\n');

        sb.append("【角色基本信息】\n");
        sb.append("姓名：").append(ctx.displayName()).append('\n');
        if (ctx.personaAge() != null) {
            sb.append("年龄：").append(ctx.personaAge()).append('\n');
        }
        if (hasText(ctx.personaOccupation())) {
            sb.append("职业：").append(ctx.personaOccupation().trim()).append('\n');
        }
        if (hasText(ctx.note())) {
            sb.append("补充说明：").append(ctx.note().trim()).append('\n');
        }
        sb.append('\n');

        if (ctx.qa() != null && !ctx.qa().isEmpty()) {
            sb.append("【问答】\n");
            int index = 1;
            for (CharacterPromptContext.Qa item : ctx.qa()) {
                if (!item.answered()) {
                    continue;
                }
                sb.append("Q").append(index++).append("：")
                        .append(truncate(item.question(), 300)).append('\n');
                sb.append("A").append(index - 1).append("：")
                        .append(truncate(item.answer(), MAX_ANSWER_LEN)).append('\n');
            }
            sb.append('\n');
        }

        sb.append("要求：\n");
        sb.append("1. 角色必须紧扣上述问答透露出的偏好、经历与语气，不要泛泛而谈。\n");
        sb.append("2. 性格、口头禅、示例对话要具体可模仿，避免空洞形容词堆砌。\n");
        sb.append("3. 严格只输出一个 JSON 对象，不要任何解释文字，不要 markdown 代码块。\n\n");

        sb.append("输出的 JSON 必须包含以下字段：\n");
        sb.append("{\n");
        sb.append("  \"name\": \"角色名\",\n");
        sb.append("  \"tagline\": \"一句话人设，20 字以内\",\n");
        sb.append("  \"personality\": [\"性格标签\", \"另一个标签\"],\n");
        sb.append("  \"background\": \"150 字以内的背景故事\",\n");
        sb.append("  \"speakingStyle\": \"语言风格描述\",\n");
        sb.append("  \"catchphrases\": [\"口头禅\"],\n");
        sb.append("  \"exampleDialogue\": [{\"user\": \"用户说的话\", \"assistant\": \"角色回应\"}],\n");
        sb.append("  \"taboos\": [\"这个角色绝不会做的事\"],\n");
        sb.append("  \"systemPrompt\": \"最终可直接使用的完整 system prompt，300-600 字，用第二人称对模型下达指令\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private String nullSafe(String text, String fallback) {
        return hasText(text) ? text.trim() : fallback;
    }
}
