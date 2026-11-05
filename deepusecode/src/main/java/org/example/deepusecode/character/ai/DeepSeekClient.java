package org.example.deepusecode.character.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.config.DeepSeekProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DeepSeek 客户端（OpenAI 兼容的 chat/completions 协议）。
 * <p>未配置 API Key 时不报错，而是返回结构化的 mock 结果，
 * 让「模板 → 作答 → 生成 → 对话」整条链路在没 key 的情况下也能跑通和演示。
 * 降级结果会通过 model = "mock" 明确标识，前端也会提示。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    public static final String MOCK_MODEL = "mock";

    private static final int MOCK_STREAM_CHUNK = 6;
    private static final long MOCK_STREAM_DELAY_MS = 25L;

    private final DeepSeekProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** AI 调用的统一返回 */
    public record AiResult(String content, String model, boolean degraded) {
    }

    public record ChatTurn(String role, String content) {
    }

    public boolean isDegradedMode() {
        return !properties.hasKey();
    }

    // ===================== 角色卡生成（同步） =====================

    public AiResult generateCharacterCard(String prompt, CharacterPromptContext ctx) {
        if (isDegradedMode()) {
            log.info("未配置 DeepSeek API Key，生成角色降级为 mock");
            return new AiResult(mockCardJson(ctx), MOCK_MODEL, true);
        }

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "你是资深 AI 角色设定师，只输出 JSON。"),
                Map.of("role", "user", "content", prompt)
        );

        String content = call(messages, properties.getCreativeTemperature(), true);
        return new AiResult(content, properties.getChatModel(), false);
    }

    // ===================== 对话（同步） =====================

    public AiResult chat(String systemPrompt, List<ChatTurn> history, String userMessage) {
        if (isDegradedMode()) {
            return new AiResult(mockChatReply(userMessage), MOCK_MODEL, true);
        }
        String reply = call(buildChatMessages(systemPrompt, history, userMessage),
                properties.getChatTemperature(), false);
        return new AiResult(reply, properties.getChatModel(), false);
    }

    // ===================== 对话（流式） =====================

    /**
     * 流式对话。每收到一段增量就回调 onDelta，最终返回完整文本。
     * 用 JDK 的 java.net.http.HttpClient 读取 SSE，避免为这件事引入 WebClient/WebFlux。
     */
    public AiResult streamChat(String systemPrompt, List<ChatTurn> history, String userMessage,
                               Consumer<String> onDelta) {
        if (isDegradedMode()) {
            String reply = mockChatReply(userMessage);
            emitMockStream(reply, onDelta);
            return new AiResult(reply, MOCK_MODEL, true);
        }

        Map<String, Object> body = buildBody(
                buildChatMessages(systemPrompt, history, userMessage),
                properties.getChatTemperature(), false, true);

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw BusinessException.aiError("构造 AI 请求失败");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.chatCompletionsUrl()))
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + properties.getKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        StringBuilder full = new StringBuilder();
        try {
            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errorBody = readAll(response.body());
                log.error("DeepSeek 流式调用失败, HTTP {}, body={}", response.statusCode(),
                        trim(errorBody, 500));
                throw toBusinessException(response.statusCode());
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if (payload.isEmpty()) {
                        continue;
                    }
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    JsonNode node = objectMapper.readTree(payload);
                    if (node.has("error")) {
                        String message = node.path("error").path("message").asText("未知错误");
                        log.error("DeepSeek 流式返回错误: {}", message);
                        throw BusinessException.aiError("AI 服务返回错误：" + trim(message, 200));
                    }
                    JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                    if (delta.isTextual() && !delta.asText().isEmpty()) {
                        full.append(delta.asText());
                        onDelta.accept(delta.asText());
                    }
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BusinessException.aiError("AI 调用被中断");
        } catch (Exception e) {
            log.error("DeepSeek 流式调用异常", e);
            throw BusinessException.aiError("AI 服务调用失败，请稍后重试");
        }

        if (full.isEmpty()) {
            throw BusinessException.aiError("AI 返回了空内容，请重试");
        }
        return new AiResult(full.toString(), properties.getChatModel(), false);
    }

    // ===================== 底层 HTTP（非流式） =====================

    private String call(List<Map<String, String>> messages, double temperature, boolean jsonMode) {
        Map<String, Object> body = buildBody(messages, temperature, jsonMode, false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getKey());

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.chatCompletionsUrl(), HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error")) {
                String providerMessage = root.path("error").path("message").asText("未知错误");
                log.error("DeepSeek 返回错误: {}", providerMessage);
                throw BusinessException.aiError("AI 服务返回错误：" + trim(providerMessage, 200));
            }

            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.error("DeepSeek 响应缺少 choices，原始响应长度={}",
                        response.getBody() == null ? 0 : response.getBody().length());
                throw BusinessException.aiError("AI 服务返回了无法识别的结果");
            }

            String content = choices.get(0).path("message").path("content").asText("");
            if (content == null || content.isBlank()) {
                throw BusinessException.aiError("AI 返回了空内容，请重试");
            }
            return content;

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.error("调用 DeepSeek 失败, HTTP {}, body={}", status, trim(e.getResponseBodyAsString(), 500));
            throw toBusinessException(status);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 DeepSeek 异常", e);
            throw BusinessException.aiError("AI 服务调用失败，请稍后重试");
        }
    }

    private Map<String, Object> buildBody(List<Map<String, String>> messages, double temperature,
                                          boolean jsonMode, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getChatModel());
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", properties.getMaxTokens());
        body.put("stream", stream);
        if (jsonMode) {
            // DeepSeek 的 JSON Output 模式，可显著降低拿到非法 JSON 的概率
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    private List<Map<String, String>> buildChatMessages(String systemPrompt,
                                                        List<ChatTurn> history,
                                                        String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        if (history != null) {
            for (ChatTurn turn : history) {
                messages.add(Map.of("role", turn.role(), "content", turn.content()));
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }

    private BusinessException toBusinessException(int status) {
        if (status == 401 || status == 403) {
            return BusinessException.aiError("DeepSeek API Key 无效或无权限，请检查配置");
        }
        if (status == 402) {
            return BusinessException.aiError("DeepSeek 账户余额不足");
        }
        if (status == 429) {
            return BusinessException.aiError("DeepSeek 请求过于频繁，请稍后再试");
        }
        return BusinessException.aiError("AI 服务暂时不可用（HTTP " + status + "）");
    }

    // ===================== mock 降级 =====================

    private void emitMockStream(String reply, Consumer<String> onDelta) {
        for (int i = 0; i < reply.length(); i += MOCK_STREAM_CHUNK) {
            int end = Math.min(i + MOCK_STREAM_CHUNK, reply.length());
            onDelta.accept(reply.substring(i, end));
            try {
                Thread.sleep(MOCK_STREAM_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private String mockCardJson(CharacterPromptContext ctx) {
        ObjectNode root = objectMapper.createObjectNode();
        String name = ctx.displayName();

        root.put("name", name);
        root.put("tagline", "样例角色 · 未配置 DeepSeek Key 时的占位人设");

        ArrayNode personality = root.putArray("personality");
        personality.add("友善直接");
        personality.add("乐于分享");
        // 用用户自定义的第一条基础信息补一个性格标签，让 mock 更贴合输入
        if (!ctx.filledFields().isEmpty()) {
            personality.add(ctx.filledFields().get(0).value() + "视角");
        }

        StringBuilder bg = new StringBuilder();
        bg.append("你是「").append(name).append("」。");
        for (CharacterPromptContext.Field field : ctx.filledFields()) {
            bg.append(field.label()).append("：").append(field.value()).append("；");
        }
        bg.append("这个角色由模板《")
                .append(ctx.setName() == null ? "未命名模板" : ctx.setName())
                .append("》的 ").append(ctx.answeredCount()).append(" 条问答拼装而成。")
                .append("（当前为 mock 数据，配置 DEEPSEEK_API_KEY 后会由模型真实生成。）");
        root.put("background", bg.toString());

        root.put("speakingStyle", "口语化，句子不长，喜欢用具体的例子说明问题。");

        ArrayNode catchphrases = root.putArray("catchphrases");
        catchphrases.add("这个问题我确实想过");
        catchphrases.add("我们具体聊聊");

        ArrayNode dialogue = root.putArray("exampleDialogue");
        ObjectNode turn = objectMapper.createObjectNode();
        turn.put("user", "你最近在忙什么？");
        turn.put("assistant", "在整理一些自己以前记下的想法，顺便想想怎么讲清楚。");
        dialogue.add(turn);

        ArrayNode taboos = root.putArray("taboos");
        taboos.add("不假装拥有自己没有的经历");
        taboos.add("不使用说教口吻");

        root.put("systemPrompt", buildMockSystemPrompt(ctx));

        return root.toString();
    }

    private String buildMockSystemPrompt(CharacterPromptContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("你现在要扮演「").append(ctx.displayName()).append("」。\n\n");
        sb.append("## 身份\n");
        sb.append("昵称：").append(ctx.displayName()).append('\n');
        for (CharacterPromptContext.Field field : ctx.filledFields()) {
            sb.append(field.label().trim()).append("：").append(field.value().trim()).append('\n');
        }
        sb.append('\n');

        if (ctx.qa() != null) {
            StringBuilder qa = new StringBuilder();
            for (CharacterPromptContext.Qa item : ctx.qa()) {
                if (item.answered()) {
                    qa.append("- 问：").append(item.question().trim())
                            .append("  答：").append(item.answer().trim()).append('\n');
                }
            }
            if (!qa.isEmpty()) {
                sb.append("## 你的经历与偏好（来自问答）\n").append(qa).append('\n');
            }
        }

        if (ctx.note() != null && !ctx.note().isBlank()) {
            sb.append("## 补充设定\n").append(ctx.note().trim()).append("\n\n");
        }

        sb.append("## 扮演要求\n");
        sb.append("始终以第一人称、口语化的方式回应，紧扣上述经历与偏好，不要跳出角色。");
        return sb.toString();
    }

    private String mockChatReply(String userMessage) {
        String prefix = "（mock 回复 · 未配置 DEEPSEEK_API_KEY）";
        String msg = userMessage == null ? "" : userMessage.trim();
        if (msg.isEmpty()) {
            return prefix + "我在，你想聊点什么？";
        }
        if (msg.contains("你好") || msg.contains("嗨") || msg.contains("在吗")) {
            return prefix + "你好，我在。想聊些什么？";
        }
        if (msg.contains("你是谁")) {
            return prefix + "我是根据你填写的模板生成的 AI 角色。配置真实 API Key 之后，我会用完整的角色设定来回应你。";
        }
        return prefix + "我收到了你的消息：「" + trim(msg, 60) + "」。"
                + "配置 DEEPSEEK_API_KEY 后，这里会由模型按角色设定真实作答。";
    }

    private String readAll(InputStream stream) {
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private String trim(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
