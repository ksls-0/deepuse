package org.example.deepusecode.character.ai;

import java.util.List;

/**
 * 生成角色卡的输入上下文。
 *
 * <p>结构对应前端的两步：先「基础信息」（昵称 + 用户自定义类别），再「问答」。
 * 单独建模是为了让 mock 降级也能产出与真实输入相关的角色卡，而不是一句"请配置 key"。</p>
 */
public record CharacterPromptContext(
        String setName,
        String setDescription,
        String personaName,
        /** 用户自定义的基础信息类别，有序。内置只有昵称，不在这里 */
        List<Field> personaFields,
        String note,
        List<Qa> qa
) {

    public record Qa(String question, String answer) {
        public boolean answered() {
            return answer != null && !answer.isBlank();
        }
    }

    /** 一条自定义基础信息，例如 (职业, 程序员) */
    public record Field(String label, String value) {
        public boolean filled() {
            return label != null && !label.isBlank() && value != null && !value.isBlank();
        }
    }

    public long answeredCount() {
        return qa == null ? 0 : qa.stream().filter(Qa::answered).count();
    }

    public String displayName() {
        return personaName == null || personaName.isBlank() ? "未命名角色" : personaName.trim();
    }

    /** 只保留填了内容的类别 */
    public List<Field> filledFields() {
        return personaFields == null ? List.of() : personaFields.stream().filter(Field::filled).toList();
    }
}
