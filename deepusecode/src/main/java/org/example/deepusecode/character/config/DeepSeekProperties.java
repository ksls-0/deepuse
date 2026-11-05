package org.example.deepusecode.character.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek 配置。
 * 用强类型绑定而不是 @Value("${deepseek.api-key}")，
 * 从根上避免“配置键名写错导致静默走默认值”那类 bug。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "deepseek.api")
public class DeepSeekProperties {

    /** 形如 https://api.deepseek.com（末尾不带 /chat/completions） */
    private String baseUrl = "https://api.deepseek.com";

    /** 为空时后端降级为结构化 mock */
    private String key = "";

    private String chatModel = "deepseek-chat";

    /** 同步生成比较慢，读超时给足 */
    private int timeoutMs = 120_000;

    private int maxTokens = 2500;

    /** 生成角色卡的采样温度，人设创作需要高一点 */
    private double creativeTemperature = 1.3;

    /** 对话时的采样温度 */
    private double chatTemperature = 1.0;

    public boolean hasKey() {
        return key != null && !key.isBlank();
    }

    public String chatCompletionsUrl() {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }
}
