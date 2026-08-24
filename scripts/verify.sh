#!/usr/bin/env bash
#
# IntelliJ Plugin Verifier をかけ、IDE ごとの判定を一覧で出す。
#
#   scripts/verify.sh
#
# 対象の IDE 版は build.gradle.kts の intellijPlatform 設定に従う。
# 初回は IDE の取得で数分かかる。

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

handle_help "${BASH_SOURCE[0]}" "$@"

info "Plugin Verifier を実行します (初回は IDE のダウンロードで時間がかかります)"
status=0
"$GRADLE" verifyPlugin "$@" || status=$?

reports_root="build/reports/pluginVerifier"
if [ -d "$reports_root" ]; then
  echo
  info "判定"
  shopt -s nullglob
  for verdict in "$reports_root"/*/plugins/*/*/verification-verdict.txt; do
    # .../pluginVerifier/<IDE>/plugins/<pluginId>/<version>/verification-verdict.txt
    ide="$(basename "$(dirname "$(dirname "$(dirname "$(dirname "$verdict")")")")")"
    text="$(cat "$verdict")"
    case "$text" in
      Compatible*problem*) warn "$(printf '%-22s %s' "$ide" "$text")" ;;
      Compatible*)         ok   "$(printf '%-22s %s' "$ide" "$text")" ;;
      *)                   warn "$(printf '%-22s %s' "$ide" "$text")" ;;
    esac
  done
  shopt -u nullglob
  echo
  dim "詳細: $reports_root/<IDE>/plugins/<プラグイン id>/<版>/"
fi

[ "$status" -eq 0 ] || die "検証で問題が見つかりました"
ok "すべての IDE と互換です"
