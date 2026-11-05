# 角色工坊（deepusecode + hello）

「自己写问题 → 自己作答 → AI 生成角色 → 站内对话」的 UGC 闭环。
模板与角色都可以选择公开，进入广场供他人浏览、一键导入或直接使用。

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
AI_DAILY_GENERATION_LIMIT=20             # 每用户 24h 内最多生成多少次角色
APP_ADMIN_ACCOUNTS=                     # 管理员账号白名单，逗号分隔
```

- `JWT_SECRET` **缺失或仍是模板占位值时应用会拒绝启动**（防止用弱密钥上线）。
- `.env` 已被 `.gitignore` 忽略；服务器上用 systemd `EnvironmentFile` 加载。
- **Redis 是注册功能的硬依赖**（验证码、重发限制、每日上限都存这里），必须先起好。
- `DEEPSEEK_API_KEY` 从 <https://platform.deepseek.com/api_keys> 申请（`sk-` 开头，
  只显示一次）。**平台要先充值**，余额为 0 时接口返回 402 `Insufficient Balance`。
  填法、验证方式和成本控制见 `deploy/DEPLOY-DOCKER.md` §8。

#### 数据库迁移（Flyway）

schema 现在由 **Flyway 单一管理**，`spring.jpa.hibernate.ddl-auto=validate`
（Hibernate 只校验不改表，启动时就能发现实体与库的漂移）。

```
src/main/resources/db/migration/V1__init.sql      ← 8 张业务表的完整结构（含唯一索引）
src/main/resources/db/migration/V2__moderation.sql ← 权限角色、内容下架字段、举报表
src/main/resources/db/migration/V3__persona_fields.sql ← 自定义基础信息类别表（老数据自动迁移）
```

- **全新空库**：启动时自动执行 V1 建表。
- **已有库**（表是早期 `ddl-auto` 建的、没有 `flyway_schema_history`）：
  `baseline-on-migrate=true` + `baseline-version=1` 会把 V1 记成 baseline
  （`type=BASELINE`，不重复执行），从此平滑接管，不需要手工跑 SQL。
- **后续加表改表**：新增 `V2__xxx.sql`、`V3__xxx.sql` 即可，**不要修改已发布的 V 文件**
  （Flyway 会校验 checksum 并拒绝启动）。

`dev`（H2 演示）和 `test` profile 都关闭了 Flyway，由 Hibernate 直接建表。

> 早期手工写的 `db/legacy/manual-v2-auth.sql` 保留作历史参考，Flyway 不会扫描它。

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
| 生成/对话耗时 | 日志里的 `角色生成完成: ... 耗时=xxms` / `流式对话完成: ... 耗时=xxms` |
| JWT 密钥指纹 | 启动日志 `JWT 密钥已加载，指纹 sha256:xxxxxxxx`（只打印指纹，不泄漏密钥） |

> 除 `health`、`info` 外的 actuator 端点**未暴露**，`/actuator/env` 等访问不到。
---

## 二、数据模型

| 表 | 说明 |
|---|---|
| `app_user` | 用户。**表名不用 `user`**，因为 `user` 在 H2 / PostgreSQL 是保留字 |
| `question_set` | 模板（可复用资产），带 `visibility` 与 `import_count` |
| `question` | 模板下的有序问题 |
| `answer_sheet` | 一次作答。**同一用户对同一模板可以有多份**，用于保存不同答案版本；只内置「昵称」一个基础字段 |
| `persona_field` | 答卷里的自定义基础信息类别（如 职业/年龄/兴趣），由用户自己定义类别名与内容 |
| `answer` | 答卷中的单题答案 |
| `ai_template` | AI 生成的角色：`content` 是 system prompt，`raw_json` 是结构化角色卡 |
| `conversation` | 一次会话（用户 + 角色） |
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

### AI 生成配额

每次成功生成角色消耗 1 次额度，按用户 24 小时滚动窗口计数（Redis `ai:quota:gen:{userId}`）。

- 默认上限 20 次/24h，用 `AI_DAILY_GENERATION_LIMIT` 调整，`<= 0` 表示不限量（本地压测用）
- 超额返回 **429**：`今日 AI 生成次数已用完（上限 N 次），请明天再试`
- 策略是**先扣额度、失败退还**：AI 调用本身就有成本，只统计成功次数保护不到账单；
  但 AI 异常或落库失败会退还，不会白扣用户额度
- 配额按用户隔离，一个用户用尽不影响其他人

### 内容审核（UGC 合规）

```
POST /api/reports                              {targetType, targetId, reason, detail}  举报公开内容
GET  /api/admin/reports/pending?page&size                                             待处理队列
GET  /api/admin/reports/handled?page&size                                             已处理历史
GET  /api/admin/stats                                                                 概览
POST /api/admin/reports/{id}/handle            {action: TAKEDOWN|REJECT, note}         处理举报
POST /api/admin/{SET|TEMPLATE}/{id}/takedown   {reason}                               直接下架
POST /api/admin/{SET|TEMPLATE}/{id}/restore                                           恢复
```

**下架与作者"设为私密"是两个正交维度**：`visibility` 是作者意愿，`taken_down` 是平台处置，
后者优先。被下架的内容：

- 不出现在广场，他人打开详情返回 404
- 作者自己仍能查看和编辑，页面顶部显示下架原因
- 恢复后自动回到作者原来的公开状态，不需要他再手动公开一次
- **作者手动再切一次"公开"也救不回来**，因为平台处置优先

**举报规则**：同一人对同一内容只能举报一次（唯一索引兜底，刷举报会返回 409）；
不能举报自己的内容；一次`TAKEDOWN`会把同一内容的其他待处理举报一并结掉。

**管理员身份**：通过配置白名单授予，不需要在数据库里手工改权限角色。

```properties
app.admin.accounts=admin@example.com,13800138000
```

白名单里的账号注册即成为管理员；已存在的账号在启动时被自动提升。
权限角色写在 JWT 的 claim 里，**改动权限角色后需要重新登录**才生效。

### 真实发信

`CodeSender` 是发送通道的抽象，**邮箱通道已支持真实发送且自适应配置**：

- 配了 `SPRING_MAIL_HOST` → 通过 JavaMail 真正发信
- 没配 → 自动退化为把验证码打印到后端日志（本地开发无需邮箱账号）

配置方式（环境变量，走 Spring 标准绑定）：

```bash
SPRING_MAIL_HOST=smtp.qq.com      # QQ 邮箱；163 用 smtp.163.com
SPRING_MAIL_PORT=465              # 465 = SSL，已默认开启
SPRING_MAIL_USERNAME=你的邮箱
SPRING_MAIL_PASSWORD=授权码        # 注意：不是登录密码
MAIL_FROM=                        # 选填，发件人展示地址
```

**获取 QQ 邮箱授权码**：QQ 邮箱网页版 → 设置 → 账户 → 开启「POP3/SMTP服务」→ 生成授权码（16 位）。
163 邮箱同理（设置 → POP3/SMTP/IMAP → 开启 → 获取授权码）。

> 踩过的坑：不要写成 `spring.mail.host=${MAIL_HOST:}`。空字符串会被 Spring 当成"已配置"，
> 于是真的去连空主机，发码直接失败。所以 `spring.mail.host` 刻意不写进配置文件，
> 完全交给 `SPRING_MAIL_HOST` 环境变量，不设就不会注册 `JavaMailSender`。

发送失败时 `VerifyCodeService` 会撤销验证码和 60 秒限制并返回明确错误，
不会让用户手里攥着一个发不出去的验证码干等。

**短信通道仍是开发版**（只打日志）。接入需要：阿里云/腾讯云 AccessKey、
**已审核通过的短信签名与模板编号**（签名和模板需 1~2 天审核）。代码位置在
`SmsCodeSender.send()`，业务代码无需改动。


### 模板（需登录）
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
POST   /api/sets/{setId}/generate           同步生成角色 {sheetId?}
```

### 角色（需登录）
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

## 五、填写流程：先设定人物，再回答问题

作答分两步，顺序不能反过来：

**① 基础信息**

- **昵称**——内置的唯一字段，必填
- **基础信息类别**——由用户自己定义，例如「职业 / 古籍修复师」「年龄 / 34」「兴趣爱好 / 篆刻与茶」
- 补充说明（选填）

**② 问答**

回答模板里的问题。留空的题不会进入 AI 的角色设定。

> 设计取舍：内置字段只保留昵称，是因为"角色需要哪些维度"因模板而异——
> 描述虚拟偶像时要"代表作"，描述程序员时要"技术栈"，写死年龄段和职业反而限制表达。
> 老版本的 `persona_age` / `persona_occupation` 在 V3 迁移时会自动转成「年龄」「职业」两个自定义类别，不丢数据。

## 六、AI 生成流程

1. 前端提交答卷 → 后端拼 `CharacterPromptContext`（模板 + 问答 + 角色基本信息）。
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

## 七、测试

```bash
cd deepusecode
mvn test
```

- 使用 H2 内存库（MySQL 兼容模式），**不依赖本地 MySQL**。
- `ApiFlowIntegrationTest` 共 11 个用例：
  1. `fullJourney` — 注册 → 建模板 → 作答 → **改模板后答案不丢（id 稳定性回归）** →
     mock 生成 → 公开 → 游客浏览（校验账号已脱敏）→ 他人导入 → 同步对话两轮（校验上下文）→
     越权拦截 → 删除
  2. `removingQuestionCleansAnswers` — 删题时连带清理答案，保留题的 id 与答案不变
  3. `streamChatWorks` — SSE 逐段推送并完整落库
  4. `loginRateLimited` — 连续 5 次失败后返回 429
  5. `validationFailures` — 非法输入返回 400 且不泄漏内部信息
  6. `emailAndPhoneRegistration` — 邮箱/手机号双通道注册登录，通道互不通用
  7. `verifyCodeRules` — 验证码错误拒绝、一次性作废、跨通道不通用
  8. `sendCodeRateLimited` — 60 秒内重复发码返回 429
  9. `generationQuotaLimited` — AI 生成配额用尽返回 429，且不影响其他用户
  10. `moderationFlow` — 举报 → 下架 → 广场消失 → 恢复
  11. `moderationReject` — 举报驳回后内容不受影响、不可重复处理

---

## 八、构建与运行环境注意事项

这些都是实际踩过的坑，记录下来避免重复踩。

| 现象 | 原因 | 处理 |
|---|---|---|
| `Could not create local repository at D:\maven\repo` | Maven 的 `conf/settings.xml` 指向了不存在的仓库 | 用 `-s .build/settings.xml` 把仓库指到工作区内 |
| `version can neither be null, empty nor blank` | `annotationProcessorPaths` **不继承** `dependencyManagement` | pom 里给 Lombok 显式写 `version` |
| `spawn EPERM`（前端构建） | `thread-loader` 需要 spawn 带管道 stdio 的子进程 | `vue.config.js` 里 `parallel: false` |
| 容器里出现两份 JWT filter | Filter 带 `@Component` 被 Boot 自动注册，又挂进 Security 链 | 该类不加 `@Component`，由 `SecurityConfig` 手工 `new` |
| `MockMaker ... self-attach` 失败 | Mockito 内联 mock maker 需要 attach 外部进程 | `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 写 `mock-maker-subclass` |
| `cannot retry due to server authentication, in streaming mode` | `HttpURLConnection` 在「带 body + 401」时不返回响应 | 测试里换 `JdkClientHttpRequestFactory` |
| SSE 读成 `chunked transfer encoding ... EOF` | `RestTemplate` 会把整段 body 一次读成 `byte[]`，不适合 `text/event-stream` | 验证 SSE 用原生 `java.net.http.HttpClient` 流式读取 |
| **SSE 事件都收到了但连接一直不关闭** | 异步请求结束时容器做 ASYNC 派发，重新进 Security 链时 SecurityContext 已清空 | `SecurityConfig` 里 `dispatcherTypeMatchers(ASYNC, FORWARD, ERROR).permitAll()` |
| **`user` / `value` 建表或查询报语法错误** | 它们是 H2（及部分数据库）的保留字 | 表名用 `app_user`；列名用 `field_value` 这类加前缀的写法 |
| **`ddl-auto=update` 不给已有表补唯一索引** | Hibernate 的 update 只做「加表 / 加列」 | 换 Flyway 管理 schema，`ddl-auto=validate`；老库用 `baseline-on-migrate` 接管 |
| **配了邮件却发不出去，日志说连空主机** | `spring.mail.host=${MAIL_HOST:}` 的空字符串被当成"已配置" | `spring.mail.host` 不写进配置文件，只由 `SPRING_MAIL_HOST` 环境变量提供 |
| `-Dmaven.test.skip=true` 变成 "Unknown lifecycle phase" | PowerShell 把 `-Dx.y=z` 拆成两个参数 | 给参数加引号：`"-Dmaven.test.skip=true"` |

---

## 九、部署（服务器）

> 有两条互相独立的路线，产物是同一个单体 jar，**二选一**：
>
> - **Docker（推荐）**：`deploy/DEPLOY-DOCKER.md` —— 服务器只装 Docker，MySQL/Redis/Nginx 全跑容器，3306/6379/8081 不映射到宿主机
> - **裸机**：本文以下几节 + `deploy/DEPLOY-ALIYUN.md` —— systemd + 本机 MySQL/Redis/Nginx

### 1. 构建物

```powershell
.\build.ps1 -SkipTests
# 产物：deepusecode/target/deepusecode-0.0.1-SNAPSHOT.jar（约 66MB，含前端）
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
- **server 级 `proxy_read_timeout 180s`** —— 默认只有 60s，而生成角色/同步对话要等 DeepSeek
  （60~90s 很常见），用默认值会被提前掐断返回 504，而后端其实已经成功、额度也已扣掉

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

## 十、已知待办

- [x] ~~数据库密码 / `JWT_SECRET` / `DEEPSEEK_API_KEY` 外置到环境变量~~（已完成，见 `.env.example`）
- [x] ~~健康检查端点~~（已完成，`/actuator/health`）
- [x] ~~日志落盘与滚动~~（已完成）
- [x] ~~前端 history 路由 fallback~~（已完成，单体 jar 由 `WebMvcConfig` 处理）
- [x] ~~AI 调用耗时埋点~~（已完成）
- [ ] **项目尚未纳入 git** —— 上线前务必 `git init`，否则出问题无法回滚。
- [x] ~~无 Flyway，表结构靠 ddl-auto~~（已完成：Flyway 接管 schema，ddl-auto 改为 validate）
- [ ] 生成仍是同步阻塞，并发高时会占满 Tomcat 线程（`ai_template.gen_status` 已预留异步空间）。
- [x] ~~注册接口无验证码/无限流~~（已完成：邮箱/手机号双通道 + Redis 验证码与限流）- [x] ~~AI 生成无配额~~（已完成：按用户 24h 滚动窗口限次，Redis 计数）
- [ ] 一键导入无频率限制，广场 `sort=hot` 按 `importCount` 排序，理论上可被刷榜。
- [x] ~~广场无审核、举报、下架机制~~（已完成：举报 + 管理后台 + 强制下架/恢复）
- [ ] 软删除不彻底：删模板不级联清理 `answer_sheet` / `answer`。
- [ ] 列表接口仍有少量 N+1（角色 tagline 每次解析 raw_json，可落成一列）。
- [ ] 无单元测试（`CardJsonParser`、`PromptBuilder`、`JwtUtil`、`AccountMasker`、`LoginAttemptService` 最值得补）；前端零测试。
- [ ] JWT 有效期 24h 且无法主动失效（无黑名单 / refresh token）。
