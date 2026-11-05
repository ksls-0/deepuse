package org.example.deepusecode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端接口验证：题集 → 作答 → AI 生成模板 → 公开 → 他人导入 → 站内对话。
 * 跑在内存 H2 上，未配置 DeepSeek key 时走 mock，整条链路依然可验证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiFlowIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper mapper;

    /**
     * 默认的 SimpleClientHttpRequestFactory 基于 HttpURLConnection，在
     * 「请求带 body 且服务端返回 401」时会抛
     * "cannot retry due to server authentication, in streaming mode" 而不是把
     * 401 交回调用方。换成基于 java.net.http 的 JdkClientHttpRequestFactory 即可。
     * 这是测试客户端的限制，浏览器 / axios 不受影响。
     */
    @org.junit.jupiter.api.BeforeEach
    void useJdkHttpClient() {
        org.springframework.http.client.JdkClientHttpRequestFactory factory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(
                        java.net.http.HttpClient.newBuilder()
                                .connectTimeout(java.time.Duration.ofSeconds(10))
                                .build());
        factory.setReadTimeout(java.time.Duration.ofSeconds(60));
        rest.getRestTemplate().setRequestFactory(factory);
    }

    private record Res(int status, JsonNode body) {
        boolean ok() {
            return status == 200 && body != null && body.path("code").asInt(-1) == 0;
        }

        JsonNode data() {
            return body == null ? null : body.path("data");
        }

        String message() {
            return body == null ? "" : body.path("message").asText("");
        }
    }

    @Test
    @DisplayName("完整用户旅程：创作题集 → 作答 → 改题集 → AI 生成 → 公开 → 导入 → 对话")
    void fullJourney() {
        // ---------- 1. 未登录访问受保护接口应 401 ----------
        Res anon = get("/api/sets/mine", null);
        assertEquals(401, anon.status(), "未登录访问受保护接口应返回 401");

        // ---------- 2. 注册两个用户 ----------
        String tokenA = register("author@example.com", "secret123");
        String tokenB = register("visitor@example.com", "secret123");
        assertNotNull(tokenA);
        assertNotNull(tokenB);

        Res dup = post("/user/register", Map.of("account", "author@example.com", "password", "secret123"), null);
        assertEquals(409, dup.status(), "重复注册应返回 409");

        Res badLogin = post("/user/login", Map.of("account", "author@example.com", "password", "wrong-pass"), null);
        assertEquals(401, badLogin.status(), "密码错误应返回 401");

        // ---------- 3. A 创建题集 ----------
        long setId = createSet(tokenA, "关于我的一组问题",
                List.of("你最喜欢的书是什么？", "你最近在忙什么？", "你的口头禅是什么？"));

        // ---------- 4. A 保存答卷 ----------
        Res sheet = get("/api/sets/" + setId + "/sheet", tokenA);
        assertTrue(sheet.ok());
        List<Long> questionIds = qids(sheet);
        assertEquals(3, questionIds.size());

        long sheetId = saveSheet(tokenA, setId, questionIds,
                List.of("《三体》，反复读了三遍", "在做一个角色对话的小产品", "先把最小闭环跑通"));
        assertTrue(sheetId > 0);

        // ---------- 5. 关键回归：改题集标题/内容后，答卷不能丢 ----------
        Res renamed = put("/api/sets/" + setId, Map.of(
                "title", "关于我的一组问题（已改名）",
                "description", "改描述也不行",
                "questions", payload(questionIds, List.of(
                        "你最喜欢的书是什么？",
                        "你最近在忙什么？",
                        "你的口头禅是什么？"))
        ), tokenA);
        assertTrue(renamed.ok(), "更新题集失败: " + renamed.message());
        assertEquals("关于我的一组问题（已改名）", renamed.data().path("title").asText());
        assertEquals(questionIds, qidsFromDetail(renamed), "更新题集不应改变 question.id");

        Res sheetAfterRename = get("/api/sets/" + setId + "/sheet", tokenA);
        JsonNode answersAfterRename = sheetAfterRename.data().path("answers");
        assertEquals(3, answersAfterRename.size());
        for (JsonNode item : answersAfterRename) {
            assertFalse(item.path("content").asText("").isBlank(),
                    "改题集后原有答案不应丢失：" + item.path("question").asText());
        }

        // ---------- 6. A 生成模板（mock）----------
        Res generated = post("/api/sets/" + setId + "/generate", Map.of("sheetId", sheetId), tokenA);
        assertTrue(generated.ok(), "生成模板失败: " + generated.message());
        long templateId = generated.data().path("id").asLong();
        assertEquals("mock", generated.data().path("genModel").asText(), "未配置 key 时应标记为 mock");
        assertFalse(generated.data().path("content").asText().isBlank(), "system prompt 不应为空");
        assertTrue(generated.data().path("structured").isObject(), "应返回结构化角色卡");
        assertEquals("林渡", generated.data().path("structured").path("name").asText());

        // 同一题集可基于同一答卷再生成一份（一题集多模板）
        Res generated2 = post("/api/sets/" + setId + "/generate", Map.of("sheetId", sheetId), tokenA);
        assertTrue(generated2.ok());
        assertTrue(generated2.data().path("id").asLong() != templateId, "应生成新的模板记录");

        // ---------- 7. 私有状态：他人不可见 ----------
        assertEquals(404, get("/api/explore/sets/" + setId, null).status(), "未公开的题集不应出现在广场");
        assertEquals(404, get("/api/sets/" + setId, tokenB).status(), "他人不应读到我的题集私有详情");

        // ---------- 8. A 公开题集与模板 ----------
        assertTrue(patch("/api/sets/" + setId + "/visibility", Map.of("visibility", 1), tokenA).ok());
        assertTrue(patch("/api/templates/" + templateId + "/visibility", Map.of("visibility", 1), tokenA).ok());

        // ---------- 9. 游客与 B 都能看到广场，且作者账号已脱敏 ----------
        Res publicSets = get("/api/explore/sets", null);
        assertTrue(publicSets.ok(), "游客应能浏览题集广场");
        JsonNode mine = findByField(publicSets.data().path("items"), "id", setId);
        assertNotNull(mine, "公开题集应出现在广场列表");
        String ownerAccount = mine.path("ownerAccount").asText("");
        assertTrue(ownerAccount.contains("***"), "广场上不应出现完整账号，实际为: " + ownerAccount);
        assertFalse(ownerAccount.contains("author@"), "不应泄漏邮箱前缀全文");

        Res publicTemplates = get("/api/explore/templates", null);
        assertTrue(publicTemplates.ok(), "游客应能浏览模板广场");
        assertTrue(publicTemplates.data().path("total").asInt() >= 1);

        assertTrue(get("/api/explore/templates/" + templateId, null).ok(), "游客应能查看公开模板详情");

        // ---------- 10. B 一键导入 ----------
        Res imported = post("/api/sets/import/" + setId, null, tokenB);
        assertTrue(imported.ok(), "一键导入失败: " + imported.message());
        long importedId = imported.data().path("id").asLong();
        assertTrue(importedId != setId, "导入应生成副本");
        assertEquals(3, imported.data().path("questionCount").asInt(), "题目应被完整复制");
        assertEquals(0, imported.data().path("visibility").asInt(), "导入的副本默认私密");

        assertEquals(409, post("/api/sets/import/" + setId, null, tokenA).status(), "不能导入自己创建的题集");

        // ---------- 11. B 用 A 的公开模板开对话 ----------
        long conversationId = createConversation(tokenB, templateId);

        Res chat1 = post("/api/conversations/" + conversationId + "/chat", Map.of("message", "你好"), tokenB);
        assertTrue(chat1.ok(), "发送消息失败: " + chat1.message());
        assertEquals("user", chat1.data().path("userMessage").path("role").asText());
        assertEquals("assistant", chat1.data().path("assistantMessage").path("role").asText());
        assertFalse(chat1.data().path("assistantMessage").path("content").asText().isBlank());

        assertTrue(post("/api/conversations/" + conversationId + "/chat",
                Map.of("message", "再说说你自己"), tokenB).ok());

        Res history = get("/api/conversations/" + conversationId + "/messages", tokenB);
        assertTrue(history.ok());
        assertEquals(4, history.data().path("total").asInt(), "两轮对话应留存 4 条消息");

        // ---------- 12. 越权保护 ----------
        assertEquals(404, get("/api/conversations/" + conversationId, tokenA).status(),
                "他人不应读取我的会话");

        Res privateTpl = post("/api/sets/" + setId + "/generate", Map.of("sheetId", sheetId), tokenA);
        long privateTemplateId = privateTpl.data().path("id").asLong();
        assertEquals(404, post("/api/conversations", Map.of("templateId", privateTemplateId), tokenB).status(),
                "未公开的模板他人不应能使用");

        // ---------- 13. 清理 ----------
        assertTrue(delete("/api/conversations/" + conversationId, tokenB).ok());
        assertTrue(delete("/api/sets/" + importedId, tokenB).ok());
        assertEquals(404, get("/api/sets/" + importedId, tokenB).status(), "删除后应不可见");
    }

    @Test
    @DisplayName("删除题目时连带清理对应答案，不留孤儿数据")
    void removingQuestionCleansAnswers() {
        String token = register("cleanup@example.com", "secret123");
        long setId = createSet(token, "清理测试", List.of("问题一", "问题二", "问题三"));

        List<Long> ids = qids(get("/api/sets/" + setId + "/sheet", token));
        saveSheet(token, setId, ids, List.of("答一", "答二", "答三"));

        // 只保留前两题
        Res updated = put("/api/sets/" + setId, Map.of(
                "title", "清理测试",
                "questions", payload(ids.subList(0, 2), List.of("问题一", "问题二"))
        ), token);
        assertTrue(updated.ok(), updated.message());
        assertEquals(2, updated.data().path("questionCount").asInt());

        Res sheet = get("/api/sets/" + setId + "/sheet", token);
        JsonNode answers = sheet.data().path("answers");
        assertEquals(2, answers.size(), "答卷里应只剩两题");
        assertEquals(ids.get(0), answers.get(0).path("questionId").asLong(), "保留题目的 id 不应变化");
        assertEquals("答一", answers.get(0).path("content").asText(), "保留题目的答案不应丢失");
    }

    @Test
    @DisplayName("SSE 流式对话：逐段推送并完整落库")
    void streamChatWorks() throws Exception {
        String token = register("stream@example.com", "secret123");
        long setId = createSet(token, "流式测试", List.of("你喜欢什么颜色？"));
        List<Long> ids = qids(get("/api/sets/" + setId + "/sheet", token));
        long sheetId = saveSheet(token, setId, ids, List.of("深蓝"));
        long templateId = post("/api/sets/" + setId + "/generate", Map.of("sheetId", sheetId), token)
                .data().path("id").asLong();
        long conversationId = createConversation(token, templateId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        headers.setBearerAuth(token);

        // 直接用原生 JDK HttpClient 读流，和浏览器 fetch + ReadableStream 走同一条路径。
        // （RestTemplate 的 HttpMessageConverter 会把整段 body 读成 byte[]，对 chunked 的
        //   text/event-stream 处理不友好，不适合用来验证 SSE。）
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create(rest.getRootUri() + "/api/conversations/" + conversationId + "/chat/stream"))
                .timeout(java.time.Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + token)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(Map.of("message", "你好")),
                        java.nio.charset.StandardCharsets.UTF_8))
                .build();

        java.net.http.HttpResponse<java.io.InputStream> response =
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());

        assertEquals(200, response.statusCode(), "流式接口应返回 200");

        // 关键：服务端必须在推完事件后主动关闭流。
        // 若 SecurityConfig 没放行 ASYNC 派发，emitter.complete() 会触发一次
        // AccessDeniedException，而响应已提交无法处理，连接会一直挂到超时。
        long startedAt = System.currentTimeMillis();
        StringBuilder body = new StringBuilder();
        String ioError = null;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line).append('\n');
            }
        } catch (java.io.IOException e) {
            ioError = e.getMessage();
        }
        long elapsed = System.currentTimeMillis() - startedAt;

        String text = body.toString();
        assertTrue(text.contains("event:delta"),
                "应收到 delta 增量事件；读流异常=" + ioError + "；实际响应=[" + text + "]");
        assertTrue(text.contains("event:done"),
                "应收到 done 结束事件；读流异常=" + ioError + "；实际响应=[" + text + "]");
        assertTrue(elapsed < 15_000,
                "服务端应在推完后立即关闭流，实际耗时 " + elapsed + "ms（疑似 ASYNC 派发被拦截）");

        Res messages = get("/api/conversations/" + conversationId + "/messages", token);
        assertEquals(2, messages.data().path("total").asInt(), "流式对话结束后应落库两条消息");
    }

    @Test
    @DisplayName("登录失败限流：连续失败后返回 429")
    void loginRateLimited() {
        String account = "bruteforce@example.com";
        register(account, "secret123");

        for (int i = 0; i < 5; i++) {
            Res attempt = post("/user/login", Map.of("account", account, "password", "wrong-" + i), null);
            assertEquals(401, attempt.status(), "第 " + (i + 1) + " 次错误密码应返回 401");
        }

        Res blocked = post("/user/login", Map.of("account", account, "password", "wrong-6"), null);
        assertEquals(429, blocked.status(), "超过阈值后应返回 429");
        assertTrue(blocked.message().contains("失败次数"), "应给出可读的限流提示：" + blocked.message());

        // 正确密码也应被临时拒绝，避免绕过
        assertEquals(429, post("/user/login",
                Map.of("account", account, "password", "secret123"), null).status());
    }

    @Test
    @DisplayName("参数校验：非法输入应返回 400 且不泄漏内部信息")
    void validationFailures() {
        String token = register("validator@example.com", "secret123");

        Res emptyTitle = post("/api/sets", Map.of(
                "title", "",
                "questions", List.of(Map.of("content", "问题"))
        ), token);
        assertEquals(400, emptyTitle.status());

        Res emptyQuestion = post("/api/sets", Map.of(
                "title", "空问题",
                "questions", List.of(Map.of("content", "   "))
        ), token);
        assertEquals(400, emptyQuestion.status(), "空白问题应被拒绝");

        assertEquals(404, get("/api/sets/999999", token).status());

        assertEquals(400, post("/user/register",
                Map.of("account", "不是邮箱", "password", "secret123"), null).status());
        assertEquals(400, post("/user/register",
                Map.of("account", "short@example.com", "password", "123"), null).status());

        Res notFound = get("/api/sets/999999", token);
        if (notFound.body() != null) {
            String message = notFound.message().toLowerCase();
            assertFalse(message.contains("exception"), "错误信息不应包含异常类名");
            assertFalse(message.contains("select"), "错误信息不应泄露 SQL");
        }
    }

    // ===================== helpers =====================

    private List<Long> qids(Res sheetResponse) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : sheetResponse.data().path("answers")) {
            ids.add(item.path("questionId").asLong());
        }
        return ids;
    }

    /** SetDetailVO 里的题目在 questions[]，字段名是 id（不是 questionId） */
    private List<Long> qidsFromDetail(Res detailResponse) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : detailResponse.data().path("questions")) {
            ids.add(item.path("id").asLong());
        }
        return ids;
    }

    private List<Map<String, Object>> payload(List<Long> ids, List<String> contents) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            questions.add(Map.of("id", ids.get(i), "content", contents.get(i)));
        }
        return questions;
    }

    private JsonNode findByField(JsonNode array, String field, long expected) {
        for (JsonNode node : array) {
            if (node.path(field).asLong() == expected) {
                return node;
            }
        }
        return null;
    }

    private long createSet(String token, String title, List<String> questions) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (String question : questions) {
            payload.add(Map.of("content", question));
        }
        Res res = post("/api/sets", Map.of(
                "title", title,
                "description", "集成测试用题集",
                "questions", payload
        ), token);
        assertTrue(res.ok(), "创建题集失败: " + res.message());
        return res.data().path("id").asLong();
    }

    private long saveSheet(String token, long setId, List<Long> questionIds, List<String> answers) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < questionIds.size(); i++) {
            items.add(Map.of("questionId", questionIds.get(i), "content", answers.get(i)));
        }
        Res res = put("/api/sets/" + setId + "/sheet", Map.of(
                "personaName", "林渡",
                "personaAge", 28,
                "personaOccupation", "独立开发者",
                "note", "喜欢深夜写代码",
                "answers", items
        ), token);
        assertTrue(res.ok(), "保存答卷失败: " + res.message());
        return res.data().path("id").asLong();
    }

    private long createConversation(String token, long templateId) {
        Res res = post("/api/conversations", Map.of("templateId", templateId), token);
        assertTrue(res.ok(), "创建会话失败: " + res.message());
        return res.data().path("id").asLong();
    }

    private String register(String account, String password) {
        Res res = post("/user/register", Map.of("account", account, "password", password), null);
        assertTrue(res.ok(), "注册失败(" + account + "): " + res.message());
        String token = res.data().path("token").asText();
        assertFalse(token.isBlank());
        return token;
    }

    private Res get(String path, String token) {
        return exchange(HttpMethod.GET, path, null, token);
    }

    private Res post(String path, Object body, String token) {
        return exchange(HttpMethod.POST, path, body, token);
    }

    private Res put(String path, Object body, String token) {
        return exchange(HttpMethod.PUT, path, body, token);
    }

    private Res patch(String path, Object body, String token) {
        return exchange(HttpMethod.PATCH, path, body, token);
    }

    private Res delete(String path, String token) {
        return exchange(HttpMethod.DELETE, path, null, token);
    }

    private Res exchange(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = rest.exchange(path, method, entity, String.class);
        return new Res(response.getStatusCode().value(), parse(response.getBody()));
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }
}
