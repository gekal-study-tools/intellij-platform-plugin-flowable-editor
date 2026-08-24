#!/usr/bin/env bash
#
# テストを実行する。
#
#   scripts/test.sh                       すべて実行
#   scripts/test.sh BpmnModelParserTest   クラス名で絞り込み
#   scripts/test.sh --rerun               キャッシュを無視して再実行
#
# ハイフンで始まらない引数はテスト名のフィルタとして扱い、
# それ以外はそのまま Gradle に渡す。

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

args=()
for arg in "$@"; do
  case "$arg" in
    -*) args+=("$arg") ;;
    *)  args+=("--tests" "*${arg}*") ;;
  esac
done

info "テストを実行します"
status=0
"$GRADLE" test ${args[@]+"${args[@]}"} || status=$?

report="build/reports/tests/test/index.html"
if [ "$status" -ne 0 ]; then
  [ -f "$report" ] && dim "レポート: $report"
  die "テストが失敗しました"
fi

ok "テストは通りました"
# 描画テストが図を書き出しているので、目視で確かめたいときの入口を出しておく
if [ -d build/reports/bpmn-render ]; then
  dim "描画結果: build/reports/bpmn-render/ (scripts/render.sh で開けます)"
fi
