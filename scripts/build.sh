#!/usr/bin/env bash
#
# プラグインの配布用 zip をビルドする。
#
#   scripts/build.sh              通常ビルド
#   scripts/build.sh --clean      クリーンしてからビルド
#
# 追加の引数はそのまま Gradle に渡る。

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

handle_help "${BASH_SOURCE[0]}" "$@"

tasks=()
args=()
for arg in "$@"; do
  case "$arg" in
    --clean) tasks+=("clean") ;;
    *) args+=("$arg") ;;
  esac
done
tasks+=("buildPlugin")

info "プラグイン ${C_OK}$(plugin_version)${C_OFF} をビルドします"
"$GRADLE" "${tasks[@]}" ${args[@]+"${args[@]}"}

zip="$(latest_plugin_zip)"
[ -n "$zip" ] || die "zip が生成されませんでした"

ok "作成しました: $zip ($(du -h "$zip" | cut -f1))"
dim "IDE に入れるには: scripts/install.sh"
