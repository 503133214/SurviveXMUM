#!/usr/bin/env bash
# SurviveXMUM 前端一键部署。后端在 SurviveXMUM-server 仓库里单独部署，互不影响。
set -euo pipefail
cd "$(dirname "$0")"

echo "==> [1/4] 拉取最新代码"
git pull --ff-only || echo "   (非 git 环境或无更新，跳过)"

echo "==> [2/4] 构建并启动前端容器"
# --force-recreate：只有镜像内容变化时 compose 默认可能不重建容器，会出现
# 「构建成功但页面还是旧的」，这里强制重建。
docker compose up -d --build --force-recreate

echo "==> [3/4] 清理悬空镜像"
docker image prune -f

echo "==> [4/4] 当前状态"
docker compose ps
