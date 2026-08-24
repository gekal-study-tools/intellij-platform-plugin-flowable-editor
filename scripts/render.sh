#!/usr/bin/env bash
#
# テスト用の BPMN 定義を PNG に描き出し、開いて目視確認する。
# プレビューの描画を触ったときの確認用。
#
#   scripts/render.sh          描画してディレクトリを開く
#   scripts/render.sh --no-open 描画だけする
#
# 出力先: build/reports/bpmn-render/

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

output="build/reports/bpmn-render"

info "描画テストを実行します"
# キャッシュが効くと画像が更新されないので毎回走らせる
"$GRADLE" test --tests '*BpmnDiagramPainterTest*' --rerun

[ -d "$output" ] || die "画像が出力されませんでした"

echo
for png in "$output"/*.png; do
  [ -e "$png" ] || continue
  ok "$png"
done

if [ "${1:-}" != "--no-open" ]; then
  open_path "$output"
fi
