#!/usr/bin/env bash
# SurviveXMUM 前端一键部署。后端在 SurviveXMUM-server 仓库里单独部署，互不影响。
#
# 用法：
#   ./deploy.sh          拉取当前分支的更新并部署（维护者手动部署）
#   ./deploy.sh main     切到 origin/main 的最新提交并部署（GitHub Actions 经 ci-deploy.sh 调用）
set -euo pipefail
cd "$(dirname "$0")"

# 手动部署和自动部署可能同时触发，排队执行，别让两次 compose 构建互相踩
exec 9>/var/lock/survivexmum-frontend-deploy.lock
flock 9

BRANCH="${1:-}"
if [ -n "$BRANCH" ] && ! [[ "$BRANCH" =~ ^[A-Za-z0-9._/-]+$ ]]; then
  echo "非法分支名：$BRANCH" >&2
  exit 2
fi

echo "==> [1/4] 拉取最新代码"
if [ -n "$BRANCH" ]; then
  # 服务器上不改代码：直接让本地分支对齐远端。工作区有改动时 checkout 会失败并中止部署。
  git fetch --prune origin "$BRANCH"
  git checkout -B "$BRANCH" "origin/$BRANCH"
else
  git pull --ff-only || echo "   (非 git 环境或无更新，跳过)"
fi
echo "   当前版本：$(git log -1 --format='%h %s' 2>/dev/null || echo unknown)"

echo "==> [2/4] 构建并启动前端容器"
# --force-recreate：只有镜像内容变化时 compose 默认可能不重建容器，会出现
# 「构建成功但页面还是旧的」，这里强制重建。构建失败时 set -e 会在这里中止，旧容器继续服务。
docker compose up -d --build --force-recreate

echo "==> [3/4] 清理悬空镜像"
docker image prune -f

echo "==> [4/4] 当前状态"
docker compose ps
