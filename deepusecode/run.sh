#!/usr/bin/env bash
# 服务器启动脚本：从同目录 .env 读取环境变量后启动后端
# 用法： ./run.sh                  （生产，读 .env）
#        ./run.sh --spring.profiles.active=dev   （H2 演示模式）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$ROOT/.env"

if [[ -f "$ENV_FILE" ]]; then
  # 逐行导出，跳过注释与空行；用 set -a 让变量自动 export
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  echo "已从 .env 加载环境变量"
else
  echo "警告：未找到 $ENV_FILE ，请复制 .env.example 并填写" >&2
fi

if [[ -z "${JWT_SECRET:-}" ]]; then
  echo "错误：缺少 JWT_SECRET，应用会拒绝启动。" >&2
  exit 1
fi

JAR="$ROOT/target/deepusecode-0.0.1-SNAPSHOT.jar"
if [[ ! -f "$JAR" ]]; then
  echo "错误：未找到 $JAR ，请先执行 mvn package -DskipTests" >&2
  exit 1
fi

exec java -jar "$JAR" "$@"
