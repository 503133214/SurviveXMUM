#!/usr/bin/env bash
# GitHub Actions 自动部署的服务器端入口。
#
# 服务器上给 CI 用的那把 SSH key 在 authorized_keys 里被限定为只能执行本脚本
# （forced command + restrict：没有 shell、不能转发端口）。客户端请求的命令
# 放在 $SSH_ORIGINAL_COMMAND 里，这里只认两个：
#   deploy   部署 origin/main 的最新提交（默认）
#   status   只报告当前版本与容器状态，不做任何改动（用来验证 key 是否配好）
# 所以即使这把 key 泄露，能做的也只是「重新部署一次 main」。
set -euo pipefail
cd "$(dirname "$0")"

case "${SSH_ORIGINAL_COMMAND:-deploy}" in
  deploy)
    exec ./deploy.sh main
    ;;
  status)
    echo "version: $(git log -1 --format='%h %s')"
    echo "branch:  $(git rev-parse --abbrev-ref HEAD)"
    docker compose ps
    ;;
  *)
    echo "unsupported command: ${SSH_ORIGINAL_COMMAND}" >&2
    exit 2
    ;;
esac
