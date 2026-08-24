#!/usr/bin/env bash
#
# ktlint で Kotlin のコードスタイルを検査する。
#
#   scripts/lint.sh          検査のみ (CI と同じ)
#   scripts/lint.sh --fix    自動修正できる違反を直す
#
# 規則は .editorconfig の [*.{kt,kts}] セクションで調整する。

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

handle_help "${BASH_SOURCE[0]}" "$@"

if [ "${1:-}" = "--fix" ]; then
  info "コードスタイルを自動修正します"
  "$GRADLE" ktlintFormat
  ok "修正しました。git diff で内容を確認してください"
else
  info "コードスタイルを検査します"
  "$GRADLE" ktlintCheck
  ok "違反はありません"
fi
