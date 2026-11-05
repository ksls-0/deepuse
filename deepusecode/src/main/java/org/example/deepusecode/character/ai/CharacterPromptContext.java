package org.example.deepusecode.character.ai;

import java.util.List;

/**
 * 生成角色卡的输入上下文。
 * 单独建模是为了让 mock 降级也能产出与真实输入相关的角色卡，而不是一句“请配置 key”。
 */
public record CharacterPromptContext(
        String setName,
        String setDescription,
        String personaName,
        Integer personaAge,
        String personaOccupation,
        String note,
        List<Qa> qa
) {

    public record Qa(String question, String answer) {
        public boolean answered() {
            return answer != null && !answer.isBlank();
        }
    }

    public long answeredCount() {
        return qa == null ? 0 : qa.stream().filter(Qa::answered).count();
    }

    public String displayName() {
        return personaName == null || personaName.isBlank() ? "未命名角色" : personaName.trim();
    }
}
