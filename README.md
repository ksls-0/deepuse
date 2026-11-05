# 角色工坊（deepusecode + hello）

「自己写问题 → 自己作答 → AI 生成角色模板 → 站内对话」的 UGC 闭环。
题集与模板都可以选择公开，进入广场供他人浏览、一键导入或直接使用。

```
deepusecode/   Spring Boot 3.2.5 + Java 17，端口 8081
hello/         Vue 3 + Pinia + vue-router，端口 8082
.build/        本地 Maven 仓库与 settings.xml（仅构建用，不参与业务）
```

---


## 一、快速启动

### 0. 环境变量（唯一需要手工准备的东西）

所有密钥与数据库凭据都从环境变量读取，**仓库里不含任何明文凭据**。

```bash
cd deepusecode
cp .env.example .env      # 然后编辑 .env
```

`.env` 内容：

```ini
DB_USERNAME=root
DB_PASSWORD=你的数据库密码
JWT_SECRET=至少32位的随机字符串          # openssl rand -base64 48
REDIS_HOST=localhost                     # 注册验证码依赖 Redis
REDIS_PORT=6379
REDIS_PASSWORD=
DEEPSEEK_API_KEY=                        # 留空则走 mock，链路仍可跑通
```

- `JWT_SECRET` **缺失或仍是模板占位值时应用会拒绝启动**（防止用弱密钥上线）。
- `.env` 已被 `.gitignore` 忽略；服务器上用 systemd `EnvironmentFile` 加载。
- **Redis 是注册功能的硬依赖**（验证码、重发限制、每日上限都存这里），必须先起好。

#### 老库升级注意

邮箱/手机号注册给 `app_user` 加了唯一索引，但 `ddl-auto=update` **不会给已有表补索引**。
老库升级请手工执行一次：

```bash
mysql -u root -p testlogin < deepusecode/src/main/resources/db/migration-v2-auth.sql
```

全新空库不需要执行。

### 1. 本地开发（前端热更新 + 后端 jar）

```bash
# 终端 0：Redis（注册功能依赖；本机 Redis 若报 MISCONF，把 dir 指到可写目录）
redis-server --dir "D:/your/writable/path"

# 终端 A：后端
cd deepusecode
mvn package -DskipTests
.\run.ps1                 # Windows；Linux 用 ./run.sh

# 终端 B：前端（http://localhost:8082，热更新）
cd hello
npm install
npm run serve
```

访问 **http://localhost:8082**。
`vue.config.js` 把 `/api`、`/user` 代理到 8081，前端全走相对路径，**开发期不需要 CORS**。

### 2. 免 MySQL 演示模式

```bash
.\run.ps1 -Profile dev      # 或 ./run.sh --spring.profiles.active=dev
```

用 H2 文件库，数据落在 `deepusecode/.data/`，重启不丢；删掉该目录即可重置。

### 3. 单体部署构建（推荐上线用）

```powershell
.\build.ps1 -SkipTests
```

它会：编译前端 → 把 `hello/dist` 同步进 `deepusecode/src/main/resources/static/` → 打出单个可执行 jar。
产物 `deepusecode/target/deepusecode-0.0.1-SNAPSHOT.jar` **同时托管前端页面和 API**，因此：

- 天然同源，**不需要配置 CORS**，也不需要单独部署前端
- 前端 history 路由（`/explore`、`/sets/3/fill`）由 `WebMvcConfig` 回退到 `index.html`
- 启动后直接访问 **http://服务器:8081**

详细的服务器部署（Nginx / systemd / TLS）见 `deploy/` 目录与下文「九、部署」。

### 4. 健康检查与日志

| 项 | 位置 |
|---|---|
| 健康检查 | `GET /actuator/health` → `{"status":"UP","groups":["liveness","readiness"]}` |
| 就绪探针 | `GET /actuator/health/readiness` |
| 存活探针 | `GET /actuator/health/liveness` |
| 应用日志 | `deepusecode/logs/deepusecode.log`（50MB 滚动，保留 14 天，总量上限 1GB） |
| 生成/对话耗时 | 日志里的 `模板生成完成: ... 耗时=xxms` / `流式对话完成: ... 耗时=xxms` |
| JWT 密钥指纹 | 启动日志 `JWT 密钥已加载，指纹 sha256:xxxxxxxx`（只打印指纹，不泄漏密钥） |

> 除 `health`、`info` 外的 actuator 端点**未暴露**，`/actuator/env` 等访问不到。
---

## 二、数据模型

| 表 | 说明 |
|---|---|
| `app_user` | 用户。**表名不用 `user`**，因为 `user` 在 H2 / PostgreSQL 是保留字 |
| `question_set` | 题集（可复用资产），带 `visibility` 与 `import_count` |
| `question` | 题集下的有序问题 |
| `answer_sheet` | 一次作答。**同一用户对同一题集可以有多份**，用于保存不同答案版本 |
| `answer` | 答卷中的单题答案 |
| `ai_template` | AI 生成的角色模板：`content` 是 system prompt，`raw_json` 是结构化角色卡 |
| `conversation` | 一次会话（用户 + 模板） |
| `message` | 会话消息，`role` 为 user / assistant |

可见性统一用 `0=私密 / 1=公开`（`Integer` 而非 `Boolean`，避免 Hibernate 对 `is_` 前缀的映射坑）。

---

## 三、接口一览

### 认证（邮箱 / 手机号两条独立通道）

```
POST /user/code/email        {target}                      发邮箱验证码
POST /user/code/phone        {target}                      发手机验证码
POST /user/register/email    {email, code, password}       邮箱注册
POST /user/register/phone    {phone, code, password}       手机号注册
POST /user/login/email       {email, password}             邮箱登录
POST /user/login/phone       {phone, password}             手机号登录
GET  /user/me                                              当前用户
```

**验证码规则**：6 位数字、5 分钟有效、校验成功立即作废；同一目标 60 秒内只能发一次、
每天最多 10 次；两个通道的验证码互不通用。所有验证码状态存在 **Redis** 里。

**登录限流**：同「通道 + 账号 + IP」10 分钟内失败 5 次返回 429，登录成功后清零。

> 当前两个 `CodeSender` 实现是**开发版**：验证码只打到后端日志，不会真的发信/发短信。
> 接入真实通道时只需替换 `EmailCodeSender` / `SmsCodeSender` 里的 `send()` 方法体，
> 业务代码（`VerifyCodeService`、Controller）完全不用改。


### 题集（需登录）
```
POST   /api/sets                        {title, description, questions:[{content}]}
GET    /api/sets/mine?page&size
GET    /api/sets/{id}
PUT    /api/sets/{id}                   {title, description, questions:[{id?, content}]}
DELETE /api/sets/{id}                   软删
PATCH  /api/sets/{id}/visibility        {visibility: 0|1}
POST   /api/sets/import/{publicSetId}   一键导入（深拷贝到我的名下）
```

> **`PUT /api/sets/{id}` 是增量同步，不是全量覆盖。**
> 带 `id` 的题目原地更新（`question.id` 不变，已有答卷不受影响），
> 不带 `id` 的作为新增，未出现在列表里的视为删除（同时清理指向它的答案）。
> 这一点很关键：早期版本用「全删再全插」，改一次标题就会让所有答卷的答案全部失联。

### 答卷与生成（需登录）
```
GET    /api/sets/{setId}/sheets             我的全部答卷
POST   /api/sets/{setId}/sheets             新建一份空白答卷
GET    /api/sets/{setId}/sheet?sheetId=     取答卷（不传取最新；没有则返回空白结构）
PUT    /api/sets/{setId}/sheet?sheetId=     保存答卷
POST   /api/sets/{setId}/generate           同步生成模板 {sheetId?}
```

### 模板（需登录）
```
GET    /api/templates/mine?page&size
GET    /api/templates/{id}
PUT    /api/templates/{id}                  改名称 / system prompt
PATCH  /api/templates/{id}/visibility
DELETE /api/templates/{id}
```

### 广场（游客可读）
```
GET    /api/explore/sets?keyword&sort=latest|hot&page&size
GET    /api/explore/sets/{id}
GET    /api/explore/templates?keyword&sort=latest|hot&page&size
GET    /api/explore/templates/{id}
```

### 对话（需登录）
```
POST   /api/conversations                   {templateId}
GET    /api/conversations?page&size
GET    /api/conversations/{id}
GET    /api/conversations/{id}/messages?beforeId&page&size
POST   /api/conversations/{id}/chat         {message}   同步，一次返回整段
POST   /api/conversations/{id}/chat/stream  {message}   SSE 流式
DELETE /api/conversations/{id}
```

**SSE 事件格式**（`text/event-stream`）：

| event | data |
|---|---|
| `user` | 用户消息（已落库，`MessageVO`） |
| `delta` | `{"text": "增量片段"}` |
| `done` | `{userMessage, assistantMessage}` |
| `error` | `{"message": "可读错误"}` |

响应统一为 `{code, message, data}`，`code === 0` 表示成功；HTTP 状态码与 `code` 语义一致
（400/401/403/404/409/429/502/500）。

---

## 四、认证与安全

- 无状态 JWT，`Authorization: Bearer <token>`。
- `JwtAuthenticationFilter` 通过 `addFilterBefore` 挂在 Security 过滤链上（**刻意不加 `@Component`**，
  否则 Spring Boot 会把它当普通 Servlet Filter 再注册一次，导致每条请求跑两遍）。
- 放行名单只有：`/user/login`、`/user/register`、`GET /api/explore/**`、`/error`、`OPTIONS`。
- 归属校验统一走 `owner_id`，越权一律返回 **404 而不是 403**，避免探测资源是否存在。
- **登录限流**：同「账号 + IP」10 分钟内失败 5 次后返回 429，成功登录后清零。
  单机内存实现，多实例部署需换 Redis。
- **账号脱敏**：广场等公开位置只显示 `138****5678` / `ab***@x.com`，本人查看自己的内容时返回原文。
- **JWT 密钥无默认值**：未配置 `JWT_SECRET` 时启动直接失败，避免用公开默认密钥签发 token。

---

## 五、AI 生成流程

1. 前端提交答卷 → 后端拼 `CharacterPromptContext`（题集 + 问答 + 角色基本信息）。
2. `PromptBuilder` 生成要求模型输出固定 JSON 结构的提示词。
3. `DeepSeekClient` 调 `/chat/completions`，带 `response_format: json_object`。
4. `CardJsonParser` 做三级容错解析：直接解析 → 剥 ```` ```json ```` 围栏 → 截取首尾大括号。
5. 落库：`content` 存 `systemPrompt` 字段，`raw_json` 存整个结构化对象。
6. 未配置 API key 时走 `mockCardJson()`，产出与输入相关的占位角色卡，`gen_model = "mock"`，
   前端会显示「示例数据」角标；流式对话的 mock 也会按片段推送，便于验证前端。

**AI 调用不在事务里**：同步生成可能耗时数十秒，包在事务里会长时间占用数据库连接。
消息落库单独走 `ChatMessageWriter`（独立 Bean，`@Transactional` 才真正生效）。

对话上下文：取最近消息，按 **6000 字符预算** 从最新往前累加（不只是按条数），
因此长对话会保留最近的完整轮次而不是硬截 20 条。

---

## 六、测试

```bash
cd deepusecode
mvn test
```

- 使用 H2 内存库（MySQL 兼容模式），**不依赖本地 MySQL**。
- `ApiFlowIntegrationTest` 共 5 个用例：
  1. `fullJourney` — 注册 → 建题集 → 作答 → **改题集后答案不丢（id 稳定性回归）** →
     mock 生成 → 公开 → 游客浏览（校验账号已脱敏）→ 他人导入 → 同步对话两轮（校验上下文）→
     越权拦截 → 删除
  2. `removingQuestionCleansAnswers` — 删题时连带清理答案，保留题的 id 与答案不变
  3. `streamChatWorks` — SSE 逐段推送并完整落库
  4. `loginRateLimited` — 连续 5 次失败后返回 429
  5. `validationFailures` — 非法输入返回 400 且不泄漏内部信息

---


---

## 八、部署（服务器）

### 1. 构建物

```powershell
.\build.ps1 -SkipTests
# 产物：deepusecode/target/deepusecode-0.0.1-SNAPSHOT.jar（约 55MB，含前端）
```

### 2. 服务器目录结构

```
/opt/deepusecode/
├── deepusecode-0.0.1-SNAPSHOT.jar
├── .env                # chmod 600，内容同本地
├── run.sh
└── logs/               # 运行时自动创建
```

### 3. systemd

```bash
sudo cp deploy/deepusecode.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now deepusecode
sudo systemctl status deepusecode
journalctl -u deepusecode -f
```

### 4. Nginx（TLS + 反代）

```bash
sudo cp deploy/nginx.conf /etc/nginx/conf.d/deepusecode.conf
# 改掉里面的 your-domain.com 与证书路径
sudo nginx -t && sudo systemctl reload nginx
```

**SSE 的关键配置在那份文件里已经写好**，上线后务必确认：

- `proxy_buffering off;` / `gzip off;` —— 否则流式对话会变成一次性吐出
- `proxy_read_timeout 300s;` —— 必须大于后端 `SseEmitter` 的 180s 超时
- `proxy_set_header X-Forwarded-For ...` —— 登录限流按真实 IP 计数，少了这个头所有人共用代理 IP 的额度

### 5. 上线自检清单

```bash
curl https://your-domain.com/actuator/health          # {"status":"UP"}
curl -o /dev/null -w '%{http_code}\n' https://your-domain.com/            # 200（前端首页）
curl -o /dev/null -w '%{http_code}\n' https://your-domain.com/explore     # 200（SPA 深链）
curl -o /dev/null -w '%{http_code}\n' https://your-domain.com/api/sets/mine  # 401（鉴权生效）
```

再看一眼启动日志里的 `JWT 密钥已加载，指纹 sha256:xxxxxxxx`，确认线上用的是新密钥。

### 6. 服务器要求

- **JDK 17+**（jar 以 release 17 编译，JDK 11 跑不了）
- MySQL 8.x，库名 `testlogin`（连接串带 `createDatabaseIfNotExist=true` 会自动建库）
- **Redis 5+**（注册验证码、重发限制、每日上限都依赖它）

```bash
# Debian/Ubuntu 安装 Redis 并只监听本机
sudo apt install redis-server
sudo sed -i 's/^bind .*/bind 127.0.0.1 ::1/' /etc/redis/redis.conf
sudo systemctl enable --now redis-server
redis-cli ping      # 期望 PONG
```

> 若 Redis 报 `MISCONF ... not able to persist on disk`，是 `dir` 目录不可写，
> 把 `/etc/redis/redis.conf` 里的 `dir` 改到有权限的路径再重启。

上线自检时确认健康检查里 redis 组件也是 UP：

```bash
curl -s https://your-domain.com/actuator/health
```

---

## 九、已知待办

- [x] ~~数据库密码 / `JWT_SECRET` / `DEEPSEEK_API_KEY` 外置到环境变量~~（已完成，见 `.env.example`）
- [x] ~~健康检查端点~~（已完成，`/actuator/health`）
- [x] ~~日志落盘与滚动~~（已完成）
- [x] ~~前端 history 路由 fallback~~（已完成，单体 jar 由 `WebMvcConfig` 处理）
- [x] ~~AI 调用耗时埋点~~（已完成）
- [ ] **项目尚未纳入 git** —— 上线前务必 `git init`，否则出问题无法回滚。
- [ ] 无 Flyway/liquibase，表结构靠 `ddl-auto=update`；生产建议改 `validate` + 迁移脚本。
- [ ] 生成仍是同步阻塞，并发高时会占满 Tomcat 线程（`ai_template.gen_status` 已预留异步空间）。
- [x] ~~注册接口无验证码/无限流~~（已完成：邮箱/手机号双通道 + Redis 验证码与限流）- [ ] AI 生成仍无配额，建议按用户每日限次。
- [ ] 一键导入无频率限制，广场 `sort=hot` 按 `importCount` 排序，理论上可被刷榜。
- [ ] 广场暂无审核、举报、下架机制（UGC 内容合规风险）。
- [ ] 软删除不彻底：删题集不级联清理 `answer_sheet` / `answer`。
- [ ] 列表接口仍有少量 N+1（模板 tagline 每次解析 raw_json，可落成一列）。
- [ ] 无单元测试（`CardJsonParser`、`PromptBuilder`、`JwtUtil`、`AccountMasker`、`LoginAttemptService` 最值得补）；前端零测试。
- [ ] JWT 有效期 24h 且无法主动失效（无黑名单 / refresh token）。
