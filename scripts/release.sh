#!/usr/bin/env bash
#
# 版を上げて、CHANGELOG を確定し、コミットまで行う。
#
#   scripts/release.sh 0.2.0            検証してコミットまで
#   scripts/release.sh 0.2.0 --push     コミット後に push もする
#
# push すると GitHub Actions が下書きのリリースを作る。
# 公開するとリリース用のワークフローが動く、という流れ。

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

new_version="${1:-}"
[ -n "$new_version" ] || die "版を指定してください (例: scripts/release.sh 0.2.0)"
[[ "$new_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([-.][A-Za-z0-9.]+)?$ ]] \
  || die "版の書き方が想定と違います: $new_version"

do_push=false
[ "${2:-}" = "--push" ] && do_push=true

current="$(plugin_version)"
[ "$current" != "$new_version" ] || die "すでに $new_version です"

# 作業ツリーが汚れていると、何を含んだリリースか分からなくなる
[ -z "$(git status --porcelain)" ] || die "コミットされていない変更があります"

branch="$(git rev-parse --abbrev-ref HEAD)"
[ "$branch" = "main" ] || warn "main 以外のブランチにいます: $branch"

info "$current -> $new_version"

# gradle.properties の version 行だけを書き換える
tmp="$(mktemp)"
sed "s/^version[[:space:]]*=.*/version = $new_version/" gradle.properties > "$tmp"
mv "$tmp" gradle.properties

info "CHANGELOG の Unreleased を $new_version に確定します"
"$GRADLE" patchChangelog

info "検証します"
"$GRADLE" check verifyPlugin buildPlugin

git add gradle.properties CHANGELOG.md
git commit -m "リリース $new_version"

ok "コミットしました"
if $do_push; then
  info "push します"
  git push
  ok "push しました。GitHub Actions が下書きリリースを作ります"
else
  dim "push するには: git push  (または scripts/release.sh $new_version --push)"
fi
