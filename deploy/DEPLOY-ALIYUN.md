# 阿里云部署手册（角色工坊 · 单机单体部署）

> 一个 jar 同时提供前端页面和 API，Nginx 只做 TLS + 反代。
> 目标机器：阿里云 ECS，**建议 2 核 4G 起**（2 核 2G 能跑，但要开 swap，见 §1.4）。
> 本文以 **Alibaba Cloud Linux 3 / CentOS 系（dnf/yum）** 为主，Ubuntu 差异写在每个小节的 `> Ubuntu:` 里。

---

## 0. 上线前准备清单

先把这 6 件事办完，否则后面每一步都会卡：

| # | 事项 | 说明 |
|---|---|---|
| 1 | **域名** | 阿里云买一个域名，解析到 ECS 公网 IP（A 记录） |
| 2 | **ICP 备案** | 国内 ECS 用 80/443 必须先备案，否则访问会被拦截。没备案前只能先用 `IP:8081` 之类临时访问（不建议对外） |
| 3 | **安全组** | 入方向只放 **22（建议限制来源为你家 IP）、80、443**。**绝对不要放 8081 / 3306 / 6379** |
| 4 | **HTTPS 证书** | 阿里云「数字证书管理服务」申请免费证书（3 个月一续），或服务器上用 certbot |
| 5 | **账户确认** | 备案主体、域名、证书要能对应上；准备好 `openssl rand -base64 48` 生成 JWT 密钥 |
| 6 | **演示数据** | 提前准备好演示用的账号、模板、角色，别在演示现场现编 |

---

## 1. 服务器初始化

### 1.1 建业务用户

服务不要用 root 跑。

```bash
sudo useradd -m -s /bin/bash appy
sudo mkdir -p /opt/deepusecode && sudo chown -R appy:appy /opt/deepusecode
```

### 1.2 时区

```bash
sudo timedatectl set-timezone Asia/Shanghai
timedatectl            # 确认 Time zone: Asia/Shanghai (CST, +0800)
```

> 顺手把数据库、JVM 都对齐到 CST，否则日志时间和库里时间差 8 小时，排查问题会疯。

### 1.3 系统更新与基础工具

```bash
sudo dnf update -y
sudo dnf install -y git curl tar vim chrony
sudo systemctl enable --now chronyd      # 时间同步，JWT 过期判断依赖它
```

> Ubuntu: `sudo apt update && sudo apt install -y git curl tar vim chrony`

### 1.4 swap（2G 内存机型必做）

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 status=progress
sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

---

## 2. 安装运行环境

### 2.1 JDK 17

```bash
sudo dnf install -y java-17-openjdk-headless
java -version          # 必须显示 17.x
```

> Ubuntu: `sudo apt install -y openjdk-17-jre-headless`
> 注意：项目用 JDK 17 编译（`release 17`），**JDK 21 跑也没问题**，但不要装 11。

### 2.2 MySQL 8

```bash
sudo dnf install -y mysql-server
sudo systemctl enable --now mysqld
sudo mysql_secure_installation     # 一路 y，设置 root 密码
```

**只监听本机**（关键，避免 3306 被扫）：

```bash
# /etc/my.cnf.d/mysql-server.cnf 或 /etc/my.cnf 的 [mysqld] 段
sudo tee /etc/my.cnf.d/zz-binding.cnf <<'EOF'
[mysqld]
bind-address = 127.0.0.1
character-set-server = utf8mb4
collation-server = utf8mb4_0900_ai_ci
default-time-zone = '+08:00'
max_connections = 200
EOF
sudo systemctl restart mysqld
```

> Ubuntu: 配置在 `/etc/mysql/mysql.conf.d/mysqld.cnf`，服务名 `mysql`。

### 2.3 Redis

```bash
sudo dnf install -y redis
sudo systemctl enable --now redis
```

**加密码 + 只监听本机**（验证码、登录限流、AI 配额都存在这里，裸露等于把注册接口送给别人刷）：

```bash
sudo sed -i 's/^bind .*/bind 127.0.0.1 -::1/' /etc/redis/redis.conf
sudo sed -i 's/^# requirepass .*/requirepass 换成你的强密码/' /etc/redis/redis.conf
sudo sed -i 's/^protected-mode .*/protected-mode yes/' /etc/redis/redis.conf
sudo systemctl restart redis
redis-cli -a '换成你的强密码' ping     # 返回 PONG
```

> Ubuntu: 配置在 `/etc/redis/redis.conf`，服务名 `redis-server`。

### 2.4 Nginx

```bash
sudo dnf install -y nginx
sudo systemctl enable --now nginx
```

---

## 3. 建库与账号

**不要用 root 连库。** 应用用受限账号，只授权本项目这一个库。

```bash
sudo mysql -uroot -p
```

```sql
CREATE DATABASE IF NOT EXISTS testlogin
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'deepusecode'@'127.0.0.1' IDENTIFIED BY '换成你的强密码';
GRANT ALL PRIVILEGES ON testlogin.* TO 'deepusecode'@'127.0.0.1';
FLUSH PRIVILEGES;
EXIT;
```

验证：

```bash
mysql -h127.0.0.1 -udeepusecode -p -e 'SHOW DATABASES;'    # 只能看到 testlogin + information_schema
```

> 表不用手工建。首次启动时 Flyway 会自动建 V1/V2/V3，见 §9。

---

## 4. 目录结构与产物上传

服务器上的布局（与 `deploy/deepusecode.service` 里的路径一致）：

```
/opt/deepusecode/
├── deepusecode-0.0.1-SNAPSHOT.jar      # 单体 jar（前端已打进去）
├── .env                                # 密钥与环境变量，chmod 600
├── logs/                               # logback 写入，按 50MB 滚动
├── .data/                              # 预留的运行时数据目录
└── backup/                             # 历史 jar，回滚用
```

创建并设置权限：

```bash
sudo -u appy mkdir -p /opt/deepusecode/{logs,.data,backup}
sudo chmod 750 /opt/deepusecode
```

上传 jar（在你自己的 Windows 机器上执行）：

```powershell
scp "D:\software\program\26.5.13.test\deepusecode\target\deepusecode-0.0.1-SNAPSHOT.jar" `
    appy@<ECS公网IP>:/opt/deepusecode/
```

> 传输慢的话先 `gzip` 一下（69MB → 约 60MB，因为 jar 内部已经是压缩的，收益不大），
> 或者上传到阿里云 OSS 再在服务器上 `ossutil cp` 下来。

---

## 5. 生产环境变量 `.env`

在服务器上创建 `/opt/deepusecode/.env`（模板见仓库 `deepusecode/.env.example`）：

```bash
sudo -u appy tee /opt/deepusecode/.env > /dev/null <<'EOF'
# ---- 数据库 ----
DB_USERNAME=deepusecode
DB_PASSWORD=数据库密码
DB_URL=jdbc:mysql://127.0.0.1:3306/testlogin?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=utf8

# ---- JWT（必填，>=32 字符；换掉它等于让所有人重新登录）----
JWT_SECRET=用 openssl rand -base64 48 生成的随机串

# ---- Redis ----
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=Redis密码
REDIS_DB=0

# ---- DeepSeek ----
DEEPSEEK_API_KEY=sk-你的key

# ---- AI 配额（每用户 24h 内最多生成多少次角色，<=0 不限量）----
AI_DAILY_GENERATION_LIMIT=20

# ---- 邮件（不配则验证码只打到日志，演示可用，正式上线必须配）----
SPRING_MAIL_HOST=smtp.qq.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=2722944602@qq.com
SPRING_MAIL_PASSWORD=QQ邮箱授权码（不是登录密码）

# ---- 管理员白名单（逗号分隔）----
APP_ADMIN_ACCOUNTS=你的账号@example.com

# ---- 日志位置（可选，默认就是 logs/deepusecode.log）----
# LOG_FILE=logs/deepusecode.log
EOF

sudo chown appy:appy /opt/deepusecode/.env
sudo chmod 600 /opt/deepusecode/.env      # 只有 appy 能读
```

要点：

- `DB_URL` 里**去掉** `createDatabaseIfNotExist=true`——受限账号没有建库权限，库已在 §3 建好。
- `JWT_SECRET` 生成：`openssl rand -base64 48`，**不要沿用本地那个**。
- **阿里云 ECS 默认封禁 25 端口出方向**。QQ 邮箱用 465（SSL）没问题；如果你改成 587，需要把 `MAIL_SSL=false` 并自行开 STARTTLS。
- 配了 `SPRING_MAIL_HOST` 才会真实发信；不配则退化成打日志（这正是明天演示的兜底方案，见 §10）。

---

## 6. systemd 托管

```bash
sudo cp deploy/deepusecode.service /etc/systemd/system/deepusecode.service
```

按机器规格改 `ExecStart` 那行（**这一步别照抄**）：

| 机器规格 | 建议 |
|---|---|
| 2 核 4G | `-Xms512m -Xmx1536m` |
| 2 核 2G | `-Xms256m -Xmx768m`（并确保 §1.4 的 swap 已开） |

推荐写法：

```ini
ExecStart=/usr/bin/java \
  -Xms512m -Xmx1536m \
  -Duser.timezone=Asia/Shanghai \
  -Dfile.encoding=UTF-8 \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt/deepusecode/logs \
  -jar /opt/deepusecode/deepusecode-0.0.1-SNAPSHOT.jar
```

> `-Duser.timezone` 不写的话容器/JVM 可能按 UTC 走，日志里的时间会比北京时间早 8 小时。

启用：

```bash
sudo systemctl daemon-reload
sudo systemctl enable deepusecode
sudo systemctl start deepusecode
sudo systemctl status deepusecode --no-pager
```

---

## 7. Nginx 与 HTTPS

### 7.1 申请证书

**方式 A：certbot（推荐，自动续期）**

```bash
sudo dnf install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

**方式 B：阿里云免费证书**

控制台下载 Nginx 格式的 `xxx.pem` / `xxx.key`，放到 `/etc/nginx/cert/`：

```bash
sudo mkdir -p /etc/nginx/cert
# 上传后
sudo chmod 600 /etc/nginx/cert/*
```

然后把 `deploy/nginx.conf` 里两行证书路径改成你的实际路径。

### 7.2 部署配置

```bash
sudo cp deploy/nginx.conf /etc/nginx/conf.d/deepusecode.conf
sudo sed -i 's/your-domain.com/你的真实域名/g' /etc/nginx/conf.d/deepusecode.conf
sudo nginx -t          # 语法检查，必须 successful
sudo systemctl reload nginx
```

### 7.3 这份 nginx.conf 里两个不能动的关键点

1. **SSE location**（`/api/conversations/{id}/chat/stream`）必须 `proxy_buffering off` + `gzip off`，
   否则流式回答会变成"卡半天然后一次性吐出来"。
2. **server 级 `proxy_read_timeout 180s`**。
   Nginx 默认只有 60s，而 `POST /api/sets/{id}/generate` 和同步对话
   `POST /api/conversations/{id}/chat` 要等 DeepSeek 返回，**60~90 秒很常见**。
   用默认值会被 Nginx 提前掐断返回 504，但**后端其实已经生成成功、额度也已经扣掉**，
   用户重试就是白烧额度还看不到结果。

---

## 8. 防火墙（若开启了 firewalld）

```bash
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
sudo firewall-cmd --list-all
```

> 服务器本机的 firewalld 和阿里云**安全组**是两层，两层都要放行 80/443，
> 并且**两层都不要放** 8081/3306/6379。

---

## 9. 首次启动验证（逐条过）

### 9.1 看启动日志

```bash
sudo journalctl -u deepusecode -n 80 --no-pager
```

必须看到（顺序可能略有出入）：

```
Successfully validated 3 migrations
Current version of schema `testlogin`: << Empty Schema >>
Migrating schema `testlogin` to version "1 - init"
...
Migrating schema `testlogin` to version "3 - persona fields"
Successfully applied 3 migrations
Tomcat started on port 8081 (http)
```

> 如果是**已有数据的库**（从本地导过数据过来），会显示 `baseline` 而不是执行 V1，这也是正常的。

### 9.2 本机健康检查

```bash
curl -s localhost:8081/actuator/health
# {"status":"UP","groups":["liveness","readiness"]}
```

### 9.3 通过 Nginx 访问

```bash
curl -sI https://your-domain.com/ | head -3          # 200
curl -s  https://your-domain.com/actuator/health     # UP
```

浏览器打开 `https://your-domain.com`，页面能出来、深链刷新（如 `/sets`）也能出来。

### 9.4 注册与登录（演示前的正式验收）

```bash
# 发验证码（配了 SMTP 会真的发邮件）
curl -s -X POST https://your-domain.com/user/code/email \
  -H 'Content-Type: application/json' \
  -d '{"target":"你要收信的邮箱"}'
# {"code":0,"message":"验证码已发送","data":null}
```

### 9.5 生成角色要能扛过 60 秒（验证 §7.3 那个修复）

在页面上完整走一遍：建模板 → 填写 → 点生成。**故意观察耗时**：
如果这次生成用了 70~90 秒但页面正常出结果，说明 Nginx 超时修复生效。
如果 60 秒左右报"网络异常 / 504"，回去检查 `proxy_read_timeout` 是否真的加载了
（`sudo nginx -T | grep read_timeout`）。

### 9.6 流式对话

打开一个角色 → 发消息 → 文字应该**逐字出现**，而不是停顿后突然出现整段。

---

## 10. 明天演示前的检查（账密注册 + 邮箱）

演示走「邮箱 + 密码 + 验证码」注册，「手机号」通道是日志模式（`SmsCodeSender` 只打日志，未接服务商），**演示时不要点手机号**。

**开始演示前 5 分钟做这 3 件事：**

1. **确认服务活着**
   ```bash
   curl -s localhost:8081/actuator/health && sudo systemctl is-active deepusecode
   ```

2. **确认验证码能拿到**——两条路，选一条：

   **A. 配了 SMTP（推荐）**：演示时用自己的手机/邮箱实时收码。
   ```bash
   # 自己先发一条确认能收到
   curl -s -X POST https://your-domain.com/user/code/email \
     -H 'Content-Type: application/json' -d '{"target":"你的邮箱"}'
   ```

   **B. 没配 SMTP（兜底）**：验证码会打在日志里，演示时另开一个终端 `tail` 着：
   ```bash
   tail -f /opt/deepusecode/logs/deepusecode.log | grep --line-buffered '验证码'
   # 【邮箱验证码·未配置 SMTP，未真实发送】收件人=zz***, 验证码=123456
   ```
   > 这条路子在演示现场会显得"不真实"，能配 SMTP 就配。

3. **提前注册好演示账号**（管理员账号一定要提前注册，`APP_ADMIN_ACCOUNTS` 里的账号注册即成为管理员，但**权限写在 JWT 里，改完要重新登录**）。
   ```bash
   # 确认管理员生效
   curl -s https://your-domain.com/user/me -H "Authorization: Bearer <token>"
   # data.role 应该是 ADMIN
   ```

**演示时不要碰的东西**：手机号注册（未接短信）、真实 AI 生成如果有额度限制注意别重复点（每用户 24h 20 次）。

---

## 11. 日常运维

### 11.1 数据库备份（务必做）

```bash
sudo mkdir -p /opt/backup && sudo chown appy:appy /opt/backup
sudo -u appy tee /opt/deepusecode/backup-mysql.sh > /dev/null <<'EOF'
#!/bin/bash
set -euo pipefail
source /opt/deepusecode/.env
OUT=/opt/backup/testlogin-$(date +%F-%H%M).sql.gz
mysqldump -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" \
  --single-transaction --quick --default-character-set=utf8mb4 testlogin | gzip > "$OUT"
find /opt/backup -name 'testlogin-*.sql.gz' -mtime +7 -delete
EOF
sudo chmod 750 /opt/deepusecode/backup-mysql.sh

# 每天 03:10
( sudo -u appy crontab -l 2>/dev/null; \
  echo '10 3 * * * /opt/deepusecode/backup-mysql.sh >> /opt/deepusecode/logs/backup.log 2>&1' ) \
  | sudo -u appy crontab -
```

> 备份文件最好再同步一份到阿里云 OSS，否则机器挂了备份跟着一起没。

### 11.2 日志

```bash
tail -f /opt/deepusecode/logs/deepusecode.log          # 应用日志（50MB 滚动，留 14 份，上限 1GB）
sudo journalctl -u deepusecode -f                       # stdout/异常栈
ls -lh /opt/deepusecode/logs/
```

### 11.3 升级

```bash
# 0) 本机先打包： build.ps1  →  target/deepusecode-0.0.1-SNAPSHOT.jar
# 1) 备份旧 jar 和数据库
ssh appy@IP 'cp /opt/deepusecode/deepusecode-0.0.1-SNAPSHOT.jar \
             /opt/deepusecode/backup/deepusecode-$(date +%F-%H%M).jar'
ssh appy@IP '/opt/deepusecode/backup-mysql.sh'
# 2) 上传新 jar
scp deepusecode-0.0.1-SNAPSHOT.jar appy@IP:/opt/deepusecode/
# 3) 重启
ssh appy@IP 'sudo systemctl restart deepusecode && sleep 15 && curl -s localhost:8081/actuator/health'
```

> 有 Flyway 迁移时，重启会自动执行新增的 `V*.sql`，不要手工改库。

### 11.4 回滚

```bash
# 停服务 → 换回旧 jar → 看日志
sudo systemctl stop deepusecode
sudo cp /opt/deepusecode/backup/deepusecode-2026-11-06-1015.jar \
        /opt/deepusecode/deepusecode-0.0.1-SNAPSHOT.jar
sudo systemctl start deepusecode
```

**注意**：如果新版本跑过新的 Flyway 迁移（表结构变了），回滚 jar 后旧代码可能校验不过
（`ddl-auto=validate` 会启动失败）。那就要连库一起回滚：

```bash
sudo systemctl stop deepusecode
gunzip -c /opt/backup/testlogin-2026-11-06-1015.sql.gz | mysql -h127.0.0.1 -udeepusecode -p testlogin
# 同时把 flyway_schema_history 一起回到旧状态（备份里包含它）
```

---

## 12. 故障排查速查表

| 现象 | 最可能的原因 | 处理 |
|---|---|---|
| 服务起不来：`缺少 JWT 密钥` | `.env` 里 `JWT_SECRET` 没填 | 填上 >=32 字符，重启 |
| 服务起不来：`Schema validation` | 实体与库结构漂移，或 Flyway 校验和不匹配 | 看日志里具体表名；别改已发布的 `V*.sql` |
| 服务起不来：`Communications link failure` | MySQL 没起 / 密码错 / bind 地址不对 | `systemctl status mysqld`、`mysql -h127.0.0.1 -udeepusecode -p` |
| 注册时报"验证码发送失败" | SMTP 配错；阿里云封 25 端口 | 确认用 465 + SSL + **授权码**（不是登录密码） |
| 发码成功但收不到邮件 | 进了垃圾箱 / 发件邮箱限流 | 查垃圾箱；QQ 邮箱有每日发信上限 |
| Redis 报 `MISCONF` | 磁盘不可写 | `redis-cli CONFIG SET dir /var/lib/redis`（需有写权限） |
| 页面刷新 404 | SPA 回退没生效 | 确认走的是这个 jar（`static/index.html` 存在） |
| 生成角色 60 秒报 504 | `proxy_read_timeout` 还是默认 60s | 按 §7.3 改，`nginx -t` + `reload` |
| 流式对话变成一次性吐出 | SSE location 没匹配上 | `sudo nginx -T | grep -A6 'chat/stream'` |
| 所有人突然掉登录 | `JWT_SECRET` 被换过 | 正常现象，需要重新登录 |
| 磁盘满 | 日志或备份堆积 | `du -sh /opt/deepusecode/logs /opt/backup`，清理旧备份 |

---

## 附：本版本对应的备份

| 项 | 值 |
|---|---|
| 版本标签 | `v1.0.0-demo` |
| jar | `deepusecode-0.0.1-SNAPSHOT.jar`（约 66MB） |
| 本地备份路径 | `.build\backup\deepusecode-v1.0.0-demo.jar` |
| 演示形态 | 邮箱 + 密码 + 验证码注册；手机号为日志模式（不演示） |
