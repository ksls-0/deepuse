# 阿里云 Docker 部署手册（角色工坊）

> 裸机（systemd + 本机 MySQL/Redis/Nginx）的版本在 `deploy/DEPLOY-ALIYUN.md`。
> 两者产物是同一个单体 jar，二选一即可。
>
> **Docker 版的取舍**：服务器上不装 JDK/MySQL/Redis/Nginx，全部跑容器；
> 3306/6379/8081 根本不映射到宿主机，安全性天然更好；
> 代价是多一层容器，排查问题时要多看 `docker compose logs`。

---

## 0. 你要上传的东西（一共 6 项）

```
/opt/deepusecode/
├── Dockerfile
├── .dockerignore
├── docker-compose.yml
├── .env                              ← 服务器上现建，chmod 600
├── deepusecode/target/deepusecode-0.0.1-SNAPSHOT.jar   ← 路径别改，Dockerfile 按这个 COPY
├── deploy/docker/nginx.conf
├── deploy/docker/certs/              ← 证书（可选，先空着）
└── logs/                             ← 自动创建
```

> `Dockerfile` 只负责把打好的 jar 装进 JRE 镜像，**不在镜像里编译**。
> 这样构建 10 秒完成，也不依赖服务器上的 Node/Maven 源。

---

## 1. 服务器准备

### 1.1 装 Docker（Alibaba Cloud Linux 3 / CentOS 系）

```bash
sudo dnf install -y dnf-utils
sudo dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable --now docker
docker compose version        # v2.x 即可
```

> Ubuntu：`curl -fsSL https://get.docker.com | sudo sh`

### 1.2 镜像加速（国内必做，否则拉 eclipse-temurin 会卡到怀疑人生）

阿里云控制台 →「容器镜像服务」→「镜像工具」→「镜像加速器」，拿到专属地址：

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": ["https://<你的ID>.mirror.aliyuncs.com"]
}
EOF
sudo systemctl daemon-reload && sudo systemctl restart docker
docker info | grep -A2 'Registry Mirrors'
```

### 1.3 建业务用户 + 时区 + swap

```bash
sudo useradd -m -s /bin/bash appy || true
sudo usermod -aG docker appy            # 让 appy 能直接跑 docker
sudo timedatectl set-timezone Asia/Shanghai

# 2G 内存机型建议开 swap
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 status=progress
sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

> 加完组要重新登录 SSH 才生效，`docker ps` 不报权限错误即可。

### 1.4 安全组

入方向只放：**22（限制来源为你家 IP）、80、443**。
容器没映射 3306/6379/8081，所以这几个端口在宿主机上压根不存在，安全组也不用管。

---

## 2. 本机打包 jar

在你自己的 Windows 机器上：

```powershell
cd D:\software\program\26.5.13.test
.\build.ps1
# 产物：deepusecode\target\deepusecode-0.0.1-SNAPSHOT.jar（约 66MB，前端 dist 已在里面）
```

---

## 3. 上传

Windows 上打个包（保留目录结构，Dockerfile 依赖这个相对路径）：

```powershell
cd D:\software\program\26.5.13.test
tar -czf deepusecode-deploy.tar.gz `
    Dockerfile .dockerignore docker-compose.yml .env.example `
    deploy\docker `
    deepusecode\target\deepusecode-0.0.1-SNAPSHOT.jar

scp deepusecode-deploy.tar.gz appy@<ECS公网IP>:/opt/deepusecode/
```

服务器上解开：

```bash
sudo mkdir -p /opt/deepusecode && sudo chown appy:appy /opt/deepusecode
cd /opt/deepusecode
tar -xzf deepusecode-deploy.tar.gz
mkdir -p logs deploy/docker/certs
ls -R | head -30
```

> 用我打包好的 `deepusecode-v1.0.0-demo-deploy.zip` 也行，里面有 jar + `deploy/` 全套，
> 解开后把 jar 放到 `deepusecode/target/` 下即可。

---

## 4. 写 `.env`

```bash
cd /opt/deepusecode
cp .env.example .env
chmod 600 .env
openssl rand -base64 48        # 生成 JWT_SECRET，复制到下面
vim .env
```

必填 4 项，其余可以留着默认：

```ini
MYSQL_ROOT_PASSWORD=<随机强密码>
MYSQL_PASSWORD=<另一个随机强密码>
REDIS_PASSWORD=<再一个随机强密码>
JWT_SECRET=<openssl rand -base64 48 的输出>

DEEPSEEK_API_KEY=sk-xxxx        # 见 §8
APP_ADMIN_ACCOUNTS=你的账号@example.com
```

**⚠️ 两个坑，务必注意：**

1. **用不到的项整行注释掉，不要留空值。**
   尤其是 `SPRING_MAIL_*` —— 留成 `SPRING_MAIL_HOST=` 这种空串，Spring 会把它当成
   "已配置"，于是真的去连一个空主机名，发验证码直接失败。（`.env.example` 里这几行本来就是注释掉的。）
2. **`MYSQL_PASSWORD` 只在首次启动时生效。** 之后改 `.env` 里的密码，
   容器里库的密码不会跟着变（密码存在 volume 里），会导致应用连不上库。
   要改就得进容器改密码，见 §9。

---

## 5. 启动

```bash
cd /opt/deepusecode
docker compose up -d --build
docker compose ps
```

正常形态：`mysql` / `redis` 是 `healthy`，`app` 是 `healthy`，`nginx` 是 `running`。

首次启动大概 30~60 秒（含拉镜像 + Flyway 建表）。

---

## 6. 第一次启动验证（逐条过）

### 6.1 应用启动日志

```bash
docker compose logs -f app
```

必须看到：

```
Successfully applied 3 migrations        ← Flyway 建了 V1/V2/V3 三张变更
Tomcat started on port 8081 (http)
```

按 `Ctrl+C` 退出日志跟踪（容器不会停）。

### 6.2 健康检查

```bash
docker compose exec app curl -s localhost:8081/actuator/health
# {"status":"UP","groups":["liveness","readiness"]}
```

> 应该**秒回**。如果每次十几秒，说明有人打开了 `management.health.mail.enabled`
> ——邮件健康检查每次都真去连 SMTP，本项目已关闭。

### 6.3 从外网访问

```bash
curl -sI http://<ECS公网IP>/ | head -3            # 200
curl -s  http://<ECS公网IP>/actuator/health       # UP
```

浏览器打开 `http://<ECS公网IP>/`，页面出来、点进 `/sets` 刷新也还在（SPA 深链正常）。

### 6.4 注册（演示前的正式验收）

```bash
curl -s -X POST http://<ECS公网IP>/user/code/email \
  -H 'Content-Type: application/json' \
  -d '{"target":"你要收信的邮箱"}'
# {"code":0,"message":"验证码已发送","data":null}
```

没配 SMTP 的话，验证码在日志里：

```bash
docker compose logs --tail=20 app | grep 验证码
```

### 6.5 生成角色要能扛过 60 秒

页面走一遍：建模板 → 填写 → 点生成。**故意看耗时**：
如果用了 70~90 秒但正常出结果，说明 Nginx 超时配置生效。
60 秒左右报 504 就是没加载到配置：

```bash
docker compose exec nginx nginx -T | grep read_timeout    # 应该看到 180s / 300s
```

### 6.6 流式对话

打开角色 → 发消息 → 文字应逐字出现，而不是停顿后突然整段蹦出来。

---

## 7. 常用运维命令

```bash
cd /opt/deepusecode

# 看状态 / 日志
docker compose ps
docker compose logs -f app
tail -f logs/deepusecode.log            # 应用日志（50MB 滚动，留 14 份）

# 重启某个服务
docker compose restart app
docker compose restart nginx             # app 容器重建后 502，先重启 nginx（见 §9）

# 进容器
docker compose exec app sh
docker compose exec mysql mysql -uroot -p testlogin
docker compose exec redis redis-cli -a "$(grep ^REDIS_PASSWORD .env | cut -d= -f2)" ping

# 资源占用
docker stats --no-stream
```

### 7.1 备份数据库

```bash
sudo mkdir -p /opt/backup && sudo chown appy:appy /opt/backup
cd /opt/deepusecode
docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --single-transaction --quick --default-character-set=utf8mb4 testlogin' \
  | gzip > /opt/backup/testlogin-$(date +%F-%H%M).sql.gz

# 每天 03:10，保留 7 天
( crontab -l 2>/dev/null; echo '10 3 * * * cd /opt/deepusecode && docker compose exec -T mysql sh -c '"'"'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick --default-character-set=utf8mb4 testlogin'"'"' | gzip > /opt/backup/testlogin-$(date +\%F-\%H\%M).sql.gz && find /opt/backup -name "testlogin-*.sql.gz" -mtime +7 -delete' ) | crontab -
```

### 7.2 升级

```bash
# 本机打好新 jar → 上传覆盖
scp deepusecode-0.0.1-SNAPSHOT.jar appy@IP:/opt/deepusecode/deepusecode/target/

# 服务器：先给旧镜像打标，再重建
cd /opt/deepusecode
docker tag deepusecode:latest deepusecode:backup-$(date +%F-%H%M)
docker compose up -d --build
docker compose logs -f app
```

### 7.3 回滚

```bash
docker images | grep deepusecode            # 找到 backup-xxxx

# 方式 A：改 .env 里的 APP_IMAGE_TAG 指向旧镜像 —— 但 up 会重新 build，
#         所以更稳的是直接用旧镜像起一个容器：
docker compose down
docker tag deepusecode:backup-2026-11-06-1015 deepusecode:latest

# 方式 B（推荐）：把旧 jar 放回去再 build
# 上传 backup 里的 jar 覆盖 target 下的文件，然后
docker compose up -d --build
```

**注意**：如果新版本跑过新的 Flyway 迁移，旧代码配新库结构会启动失败
（`ddl-auto=validate`）。这时要连库一起回滚：

```bash
docker compose stop app
gunzip -c /opt/backup/testlogin-2026-11-06-1015.sql.gz \
  | docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" testlogin
docker compose start app
```

---

## 8. 怎么加 DeepSeek API Key

**没配 key 会怎样**：后端自动降级成 mock —— 页面全流程能跑，但生成出来的是占位角色卡
（`gen_model` 是 `mock`），对话回复是"（mock 回复 · 未配置 DEEPSEEK_API_KEY）我收到了你的消息…"。
演示要真实效果，**必须配**。

### 8.1 拿 key

1. 打开 <https://platform.deepseek.com/>，用手机号或邮箱注册登录。
2. 左侧「充值」——DeepSeek 是先充值后调用的按量计费。
   **余额为 0 时接口会直接返回 402 `Insufficient Balance`**，这是最常见的踩坑点。
   充最小额度就够跑演示（生成一张角色卡 + 几十轮对话的成本很低）。
3. 左侧「API keys」→「创建 API key」。
4. 名字随便填（比如 `deepusecode-prod`），创建后**立刻复制**——
   key 只在创建时显示一次，关掉就再也看不到了，只能删了重建。

key 长这样：`sk-` 开头的一串字符。

### 8.2 填进 `.env`

```bash
cd /opt/deepusecode
vim .env
```

```ini
DEEPSEEK_API_KEY=sk-你的真实key
```

注意：

- **不要加引号**，不要写 `DEEPSEEK_API_KEY="sk-xxx"`，引号会被当成值的一部分。
- `=` 前后不要有空格。
- `.env` 权限保持 `600`（`chmod 600 .env`），别提交到 git（已在 `.gitignore` 里）。

### 8.3 重启生效

环境变量是容器启动时注入的，改完必须重建容器：

```bash
docker compose up -d --force-recreate app
```

（`docker compose restart app` 也读不到新的 `.env`，必须 recreate。）

### 8.4 验证真的生效了

**方法 A：看日志**（最直接）

```bash
docker compose logs --tail=50 app | grep -i deepseek
```

- 配好了：**不会**出现 `未配置 DeepSeek API Key，生成角色降级为 mock`
- 没配好：这条 warn 每次生成都会打一次

**方法 B：看数据库里的 `gen_model`**

```bash
docker compose exec mysql mysql -uroot -p testlogin \
  -e "SELECT id, name, gen_model, gen_status FROM ai_template ORDER BY id DESC LIMIT 5;"
```

`gen_model = deepseek-chat` 才是真的走了 API；`mock` 就是没配成功。

**方法 C：不走应用，直接测 key 是否有效**

```bash
source <(grep DEEPSEEK_API_KEY /opt/deepusecode/.env)
curl -s https://api.deepseek.com/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DEEPSEEK_API_KEY" \
  -d '{"model":"deepseek-chat","messages":[{"role":"user","content":"hi"}],"max_tokens":5}'
```

- `{"id":...,"choices":[...]}` → key 可用
- `{"error":{"message":"Authentication Fails..."}}` → key 错了
- `{"error":{"message":"Insufficient Balance"...}}` → **充钱**

### 8.5 几个使用提醒

| 事项 | 说明 |
|---|---|
| **成本控制** | 生成角色有 `AI_DAILY_GENERATION_LIMIT=20` 兜底，但**对话目前没限流**。演示给别人用的话留意别被刷 |
| **模型名** | `deepseek-chat`（V3 系）够用；想更强可改 `deepseek.api.chat-model`，但贵 |
| **超时** | 后端 `deepseek.api.timeout-ms=120000`，Nginx 已放宽到 180s，链条是通的 |
| **key 泄露** | 直接去平台删掉重建，然后按 §8.3 更新 `.env` 并 recreate |
| **多环境** | 演示环境和正式环境建议用**不同的 key**，方便在平台上看各自的用量 |
| **额度** | 平台「用量信息」页能看每天花了多少，上线第一周建议每天看一眼 |

---

## 9. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| `docker compose up` 报 `env file .env not found` | 没建 `.env` | `cp .env.example .env` 并填值 |
| app 反复重启，日志 `缺少 JWT 密钥` | `.env` 里 `JWT_SECRET` 空 | 填 `openssl rand -base64 48` 的输出 |
| app 日志 `Access denied for user 'deepusecode'` | 改过 `.env` 里的 `MYSQL_PASSWORD`，但 volume 里是旧密码 | 见下方「改数据库密码」 |
| app 日志 `Communications link failure` | mysql 还没 healthy 就起了 | 已经用 `depends_on: service_healthy` 兜住；若仍复现，`docker compose restart app` |
| 拉镜像极慢 / 超时 | 没配镜像加速器 | 按 §1.2 配 `registry-mirrors` |
| 页面 502 | app 容器重建后 IP 变了，nginx 还缓存着旧 IP | `docker compose restart nginx` |
| 页面上 8080/8081 打不开 | 这是设计如此：app 不映射端口 | 走 nginx 的 80/443；调试时放开 compose 里注释的 `127.0.0.1:8081:8081` |
| `logs/` 里没文件 / 权限拒绝 | bind mount 目录属主不对 | `sudo chown -R 1000:1000 logs`（容器默认 root 跑则不需要） |
| 生成角色 60 秒 504 | nginx 超时没生效 | `docker compose exec nginx nginx -T \| grep read_timeout` |
| 健康检查一直 `starting` | 首次启动 Flyway + Hibernate 慢，60 秒内正常 | 等；超过 2 分钟看 `logs -f app` |
| 磁盘满 | 镜像和 volume 堆积 | `docker system df`、`docker image prune -a` |

**改数据库密码（volume 里已有数据时）**：

```bash
docker compose exec mysql mysql -uroot -p旧root密码 \
  -e "ALTER USER 'deepusecode'@'%' IDENTIFIED BY '新密码'; FLUSH PRIVILEGES;"
# 同步改 .env 里的 MYSQL_PASSWORD，然后
docker compose up -d --force-recreate app
```

**彻底重来（会丢数据）**：

```bash
docker compose down -v      # -v 会把 mysql-data / redis-data 一起删掉
```

---

## 10. Docker 版 vs 裸机版

| | Docker（本文） | 裸机（DEPLOY-ALIYUN.md） |
|---|---|---|
| 服务器要装 | 只有 Docker | JDK17 + MySQL8 + Redis + Nginx |
| 端口暴露面 | 只有 80/443 | 需要自己管防火墙，避免 8081/3306/6379 外露 |
| 首次部署耗时 | 30 分钟（多数时间在拉镜像） | 40~60 分钟（装环境 + 建库 + 配 Redis） |
| 升级 | 换 jar + `up -d --build` | 换 jar + `systemctl restart` |
| 排查问题 | 多看一层 `docker compose logs` / `exec` | 直接看 `journalctl` / 本机日志 |
| 内存占用 | 多约 200MB（每个容器的基础开销） | 更省 |
| 换机器迁移 | 拷目录 + 备份 sql 就行 | 要重装一遍环境 |

明天只做演示的话，Docker 版更稳：环境一致，出问题 `down -v` 重来一遍就行。

---

## 11. 明天演示前的最后检查（3 分钟）

```bash
cd /opt/deepusecode
docker compose ps                                   # 全 healthy
curl -s localhost:8081/actuator/health              # 秒回 UP（compose 里没映射端口就 exec app）
docker compose exec app curl -s localhost:8081/actuator/health
docker compose logs --tail=30 app | grep -i deepseek   # 不应有"未配置"
```

然后确认：

1. **DeepSeek key 已配且余额够**（§8.4 方法 C 测一下）
2. **邮箱验证码能收到**（§6.4 自己先发一条）
3. **管理员账号已注册**，`/user/me` 返回 `role: ADMIN`
4. **提前建好演示用的模板和角色**，别现场现编

> 手机号注册是日志模式（`SmsCodeSender` 只打日志，未接服务商），**演示时不要点手机号那个 tab**。
