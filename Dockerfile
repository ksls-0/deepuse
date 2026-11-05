# ============================================================
# 角色工坊 —— 运行镜像
#
# 设计取向：**不在镜像里编译**。
# 前端 + 后端由本机的 build.ps1 打成单体 jar（内含 dist），
# 这个 Dockerfile 只负责把 jar 装进 JRE 镜像里跑。
#
# 好处：构建快（10 秒）、可复现（jar 的 SHA256 可对账）、
# 不依赖服务器上装 Node/Maven、不依赖 npm 源和 Maven 源。
#
# 目录要求：本文件放在仓库根目录，构建上下文也是仓库根目录，
# 且 deepusecode/target/deepusecode-0.0.1-SNAPSHOT.jar 已存在。
# ============================================================
FROM eclipse-temurin:17-jre-jammy

# curl 只给 HEALTHCHECK 用；tzdata 保证容器内是北京时间
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
 && echo "Asia/Shanghai" > /etc/timezone \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY deepusecode/target/deepusecode-0.0.1-SNAPSHOT.jar /app/app.jar

RUN mkdir -p /app/logs /app/.data

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-Xms512m -Xmx1536m -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs"

# 堆大小按机器规格在 docker-compose.yml 里用 JAVA_TOOL_OPTIONS 覆盖：
#   2 核 4G -> -Xms512m -Xmx1536m
#   2 核 2G -> -Xms256m -Xmx768m

EXPOSE 8081

# start-period 给足：Flyway 首次建表 + Hibernate 初始化大约 20~30 秒
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# 备注：默认以 root 运行，好处是 ./logs 这种 bind mount 不用处理宿主机权限。
# 想以非 root 跑（更安全）时，在下面加：
#   RUN useradd -u 10001 -m app && chown -R 10001:10001 /app
#   USER 10001
# 同时在宿主机上 `sudo chown -R 10001:10001 ./logs`，否则日志写不进去。
