#!/bin/bash

# 启动Spring Boot应用并按环境加载配置
# 使用方法: ./start.sh [local|prod]

set -e

cd "$(dirname "$0")"

echo "同步接口契约模型..."
node ../scripts/generate-contract-models.mjs

APP_ENV="${1:-local}"
ENV_FILE=".env.${APP_ENV}"

if [ -f "$ENV_FILE" ]; then
    echo "检测到 ${ENV_FILE} 文件，加载 ${APP_ENV} 环境变量..."
    export $(grep -v '^#' "$ENV_FILE" | xargs)
elif [ -f .env ]; then
    echo "未找到 ${ENV_FILE}，回退加载 .env ..."
    export $(grep -v '^#' .env | xargs)
else
    echo "未找到环境文件，将使用默认配置启动"
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-$APP_ENV}"

echo "启动Spring Boot应用..."
echo "当前环境: ${SPRING_PROFILES_ACTIVE}"
if [ -n "$ALIYUN_OSS_ACCESS_KEY_ID" ] && [ -n "$ALIYUN_OSS_ACCESS_KEY_SECRET" ]; then
    echo "使用的OSS Bucket: ${ALIYUN_OSS_BUCKET_NAME:-quickstart-template-media}"
    echo "Endpoint: ${ALIYUN_OSS_ENDPOINT:-oss-cn-hangzhou.aliyuncs.com}"
else
    echo "未配置 OSS 凭据，将使用本地文件存储"
fi

# 启动Spring Boot应用
mvn spring-boot:run -DskipTests
