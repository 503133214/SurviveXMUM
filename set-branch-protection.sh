#!/usr/bin/env bash
# 分支保护一键配置：
#   「合并前至少需要一名成员点 Approve」由 GitHub 的分支保护规则实现
#   （Settings → Branches → Branch protection rule）。它不属于 CI 配置，
#   只有仓库管理员能改，所以提供这个脚本，一条命令把 dev 和 main 配好，
#   等价于在设置页手动勾选：
#
#     - Require a pull request before merging
#         - Required approvals: 1
#           （approve 只统计有写权限的成员，且 PR 作者不能给自己 approve，
#             即天然要求「另一名相关成员」批准后才能合并）
#         - Dismiss stale approvals：推送新提交后旧 approve 作废，需重新批准
#     - 禁止 force push、禁止删除分支
#
#   用法（需要仓库管理员的 gh 登录态）：
#     ./set-branch-protection.sh                     # 对 503133214/SurviveXMUM 的 dev、main 生效
#     REPO=owner/name ./set-branch-protection.sh     # 换仓库
#     BRANCHES="dev" ./set-branch-protection.sh      # 只配部分分支
#
#   可调项（环境变量，默认值见下方赋值）：
#     ENFORCE_ADMINS       1 = 管理员合并也要走 PR + approve；0 = 管理员不受限
#     DISMISS_STALE        1 = 新提交推送后旧 approve 作废；0 = 一次 approve 一直有效
#     REQUIRE_CHECKS       1 = 同时要求 CI 的「Test & build」通过才能合并
#     REQUIRE_CODE_OWNERS  仓库加了 .github/CODEOWNERS 后设为 1，
#                         approve 必须来自改动路径对应的负责人（按目录分工时用）
set -euo pipefail

REPO="${REPO:-503133214/SurviveXMUM}"
BRANCHES="${BRANCHES:-dev main}"
ENFORCE_ADMINS="${ENFORCE_ADMINS:-1}"
DISMISS_STALE="${DISMISS_STALE:-1}"
REQUIRE_CHECKS="${REQUIRE_CHECKS:-0}"
REQUIRE_CODE_OWNERS="${REQUIRE_CODE_OWNERS:-0}"

command -v gh >/dev/null 2>&1 || { echo "需要 GitHub CLI（gh），请先安装。" >&2; exit 1; }

bool() { case "$1" in 1|true|yes) echo true ;; *) echo false ;; esac; }

PERMS=$(gh api "repos/$REPO" --jq '.permissions.admin' 2>/dev/null) || {
  echo "访问 $REPO 失败，请确认仓库名和登录态。" >&2
  exit 1
}
if [ "$PERMS" != "true" ]; then
  echo "当前 gh 登录账号对 $REPO 没有管理员权限，分支保护只有管理员能改。" >&2
  echo "请先「gh auth switch -h github.com -u 503133214」（或重新 gh auth login）再运行本脚本。" >&2
  exit 1
fi

if [ "$(bool "$REQUIRE_CHECKS")" = true ]; then
  STATUS_CHECKS='{"strict": false, "contexts": ["Test & build"]}'
else
  STATUS_CHECKS=null
fi

BODY=$(printf '{
  "required_status_checks": %s,
  "enforce_admins": %s,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": %s,
    "require_code_owner_reviews": %s
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "lock_branch": false,
  "required_conversation_resolution": false,
  "allow_fork_syncing": true
}' "$STATUS_CHECKS" "$(bool "$ENFORCE_ADMINS")" "$(bool "$DISMISS_STALE")" "$(bool "$REQUIRE_CODE_OWNERS")")

for BRANCH in $BRANCHES; do
  echo "正在配置 $REPO 的 $BRANCH 分支..."
  gh api -X PUT "repos/$REPO/branches/$BRANCH/protection" --input - <<<"$BODY" >/dev/null
done

echo "完成：合并前现在要求至少 1 名成员 approve 后才能合并。"
echo "可在 https://github.com/$REPO/settings/branches 核对。"
