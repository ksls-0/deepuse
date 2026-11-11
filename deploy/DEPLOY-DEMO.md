# 演示版部署（Docker · 无登录）· 阿里云

> 对应 tag：`v1.0.1-demo`。特性：进站静默登录（无登录注册入口）、大众版 + 长辈版、
> 单体 jar（前端已内置）、容器内 MySQL/Redis/Nginx。
>
> 带登录功能的版本在 `v1.0.0-auth`，需要时 `git checkout v1.0.0-auth` 即可。

---

## 0-A. 当前场景：**没有域名，只有公网 IP**

这条路线比有域名简单，但有三处必须按下面来：

1. **只走 HTTP 80，不要碰 443**
   `deploy/docker/nginx.conf` 里 443 那段本来就是注释掉的，`docker-compose.yml` 里
   `443:443` 也是注释掉的 —— **保持注释，不要打开**。没有证书时打开 443 会让 nginx 起不来。

2. **不需要备案、不需要证书**
   备案是对**域名**的要求；直接用 `http://公网IP` 访问不涉及。演示地址就是
   `http://<公网IP>/`。

3. **安全组把来源 IP 卡紧一点**
   没有 HTTPS，演示账号密码和 JWT 都是明文传输。加上这台机器是公网可访问的，
   建议安全组里 80 端口只放行你自己/会场出口 IP，演示完立刻收回。
   如果必须对所有人开放，那也至少把演示时间压缩，演示结束当天改掉演示账号密码。

> 私网 IP 不需要额外配置：容器之间走 compose 内网，宿主机只监听 80。
> 你从阿里云内网访问 `http://<私网IP>/` 也能通，效果一样。

### 停旧 web 前值得先确认的一件事

如果 80 端口上现在是**别人的站点**（比如公司官网），别直接停——用 §1.5 的
「先让新栈监听 8080 验收」方案，验收通过再切 80。演示用的机器最好本身就是空闲的。

---

## 0. 先跑这三条命令，把输出发给我

```bash
cat /etc/os-release | head -3           # 发行版
sudo ss -lntp | grep -E ':80\s|:443\s'  # 谁占着 80（决定怎么停）
free -h && nproc                         # 内存与核数（决定 -Xmx）
```

再回答一个问题：

- **MySQL 用容器内的，还是复用服务器上已有的？**（有旧数据要迁的话流程不同）

> 内存对照：4G 用 `-Xmx1536m`（compose 里已是默认值），2G 改成 `-Xmx768m` 并开 swap。

---

## 1. 停掉服务器上正在运行的 web

> 原则：**先诊断，再备份，最后才停**。停之前一定要留下怎么恢复的线索。

### 1.1 诊断

```bash
sudo ss -lntp | grep -E ':80\s|:443\s'
systemctl list-units --type=service --state=running | grep -Ei 'nginx|apache|httpd|caddy'
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}'
```

### 1.2 按情况停

**A. 裸机 nginx**

```bash
sudo cp -a /etc/nginx /opt/backup-nginx-$(date +%F-%H%M)   # 先备份配置
sudo systemctl stop nginx
sudo systemctl disable nginx                                # 防止重启后又抢 80
```

**B. docker 容器占着 80**

```bash
docker inspect <容器名> > /opt/backup-container-$(date +%F).json
docker stop <容器名>
docker update --restart=no <容器名>                          # 否则重启机器又回来
```

**C. 宝塔面板**：面板里停掉站点/nginx，不要整机 `bt stop`（会连面板一起停）。

### 1.3 验收

```bash
sudo ss -lntp | grep -E ':80\s|:443\s'   # 必须没有输出
curl -I http://127.0.0.1/                 # 应为 connection refused
```

> **更稳的做法**：先不停。让新的 Docker nginx 监听 **8080**，用 `http://IP:8080`
> 验收通过后，再把 compose 里的端口映射改成 `80:80` 重建 nginx 容器。
> 这样任何一步失败，原站点都还在。

---

## 2. 装 Docker（若未装）

```bash
sudo dnf install -y dnf-utils
sudo dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable --now docker
```

镜像加速（国内必做）：阿里云控制台 → 容器镜像服务 → 镜像加速器

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<'EOF'
{ "registry-mirrors": ["https://<你的ID>.mirror.aliyuncs.com"] }
EOF
sudo systemctl daemon-reload && sudo systemctl restart docker
```

---

## 3. 上传并解包

本机已打好包：`run-logs\demo-deploy.tar.gz`（含 Dockerfile、compose、nginx 配置、
`.env.example`、以及内置前端的单体 jar）。

```bash
sudo mkdir -p /opt/deepusecode-demo && cd /opt/deepusecode-demo
# 本机：scp run-logs\demo-deploy.tar.gz root@<IP>:/opt/deepusecode-demo/
tar -xzf demo-deploy.tar.gz
mkdir -p logs deploy/docker/certs
```

> Dockerfile 按 `deepusecode/target/deepusecode-0.0.1-SNAPSHOT.jar` 这个相对路径 COPY，
> 所以**必须保留目录结构**——上面的 tar 包已经保留了。

---

## 4. 配 .env

```bash
cp .env.example .env && chmod 600 .env
openssl rand -base64 48          # 复制输出填到 JWT_SECRET
vim .env
```

必填：

```ini
MYSQL_ROOT_PASSWORD=<随机强密码>
MYSQL_PASSWORD=<另一个随机强密码>
REDIS_PASSWORD=<再一个随机强密码>
JWT_SECRET=<上面生成的值>
DEEPSEEK_API_KEY=sk-xxx
APP_ADMIN_ACCOUNTS=              # 演示版可留空（演示账号是普通 USER）
```

**两条铁律**

1. 用不到的项**整行注释掉**，不要留 `SPRING_MAIL_HOST=` 这种空值——Spring 会当成已配置，真去连空主机。
2. `MYSQL_PASSWORD` 只在**首次**启动生效，之后改 `.env` 不会改库里的密码。

---

## 5. 启动

```bash
docker compose up -d --build
docker compose ps      # 期望：mysql/redis/app healthy，nginx running
```

首次约 30~60 秒（拉镜像 + Flyway 建表）。

---

## 6. 验收

```bash
docker compose logs app | grep -E 'Successfully applied|Tomcat started'
docker compose exec app curl -s localhost:8081/actuator/health     # 应秒回 UP
curl -I http://<IP>/                                               # 200
curl -o /dev/null -w '%{http_code}\n' http://<IP>/senior           # 200
docker compose logs app | grep -i '未配置 DeepSeek'                 # 不应有输出
```

浏览器过一遍（**这就是明天的演示动线**）：

1. 打开首页 → 右上角点「长辈模式」
2. 「和角色说话」→ 选「外婆」→ 发一句「我有点饿」→ 看字是否**逐字出现**
3. 返回 →「做一个新角色」→ 选模板 → 一屏一题填完 →「生成我的角色」→ 自动进聊天
4. 页头「普通版」→ 回大众版 → 去「广场」→ 打开一个公开模板 →「一键导入」

---

## 7. 演示账号与预置数据

| 项 | 值 |
|---|---|
| 账号 / 密码 | `13800138000` / `demo123456`（普通 USER，不是管理员） |
| 我的角色 | 外婆、老陈（真 AI 生成） |
| 我的模板 | 外婆的厨房、厂里的老师傅 |

**进站自动登录**，不需要输账号密码（改动在 `hello/src/demo.js` 的 `DEMO_MODE`）。

> ⚠️ 账号密码会被打进前端 bundle，打包后能搜到。演示结束请改密码，或把
> `DEMO_MODE` 改成 `false` 重新构建（登录页文件都还在，见 `src/router/index.js` 注释）。

---

## 8. 回滚

```bash
cd /opt/deepusecode-demo && docker compose down    # 数据在 volume 里，不会丢
sudo systemctl enable --now nginx                  # 恢复原站点（情况 A）
# 或 docker start <容器名>（情况 B）
```

---

## 9. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 拉镜像卡住 | 没配镜像加速器 | §2 的 daemon.json |
| `env file .env not found` | 没建 .env | `cp .env.example .env` |
| app 反复重启，日志 `缺少 JWT 密钥` | JWT_SECRET 空 | 填上，≥32 字符 |
| 502 | app 容器重建后 IP 变了，nginx 缓存旧 IP | `docker compose restart nginx` |
| 80 被占 | 旧 web 没停干净 | 回 §1.1 重新诊断 |
| 生成 60 秒 504 | nginx 超时没生效 | `docker compose exec nginx nginx -T \| grep read_timeout`（应为 180s） |
| 页面空白 / 接口 401 | 演示账号登录失败 | 看控制台是否 `[demo] 静默登录失败`，确认该账号在库里存在 |
