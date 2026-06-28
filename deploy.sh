#!/usr/bin/env bash
# SurviveXMUM 一键部署：构建并启动前后端容器
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f backend/.env.prod ]; then
  echo "❌ 缺少 backend/.env.prod"
  echo "   首次部署请先执行：cp /etc/wiki-backend.env backend/.env.prod"
  exit 1
fi

echo "==> [1/4] 拉取最新代码"
git pull --ff-only || echo "   (非 git 环境或无更新，跳过)"

echo "==> [2/4] 构建并启动容器"
# --force-recreate：确保即使只有镜像内容变化（compose 默认可能不重建），容器也会用新镜像重建
docker compose up -d --build --force-recreate

echo "==> [3/4] 清理悬空镜像"
docker image prune -f

echo "==> [4/4] 当前状态"
docker compose ps
