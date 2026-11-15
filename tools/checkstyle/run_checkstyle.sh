#!/usr/bin/env bash
# AIDoctor 后端 Checkstyle 校验（macOS / Linux）
# 首次运行会自动下载 Checkstyle jar（约 20MB），需联网。

set -euo pipefail

CHECKSTYLE_VERSION="10.17.0"
JAR="tools/checkstyle/checkstyle-${CHECKSTYLE_VERSION}-all.jar"
URL="https://github.com/checkstyle/checkstyle/releases/download/checkstyle-${CHECKSTYLE_VERSION}/checkstyle-${CHECKSTYLE_VERSION}-all.jar"

if [ ! -f "$JAR" ]; then
  echo "[INFO] 未找到 Checkstyle，正在下载 ${CHECKSTYLE_VERSION} ..."
  mkdir -p "$(dirname "$JAR")"
  curl -fL "$URL" -o "$JAR"
fi

if [ ! -f "$JAR" ]; then
  echo "[ERROR] 下载失败，请手动下载并放到 $JAR"
  echo "         $URL"
  exit 1
fi

echo "[INFO] 运行 Checkstyle（配置 checkstyle.xml，检查 src）..."
java -jar "$JAR" -c checkstyle.xml src
