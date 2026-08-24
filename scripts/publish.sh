#!/usr/bin/env bash
#
# JetBrains Marketplace に更新版を公開する。
#
#   scripts/publish.sh                 安定版として公開する
#   scripts/publish.sh --channel eap   事前公開チャンネルに出す
#   scripts/publish.sh --dry-run       公開せず、事前確認だけ行う
#   scripts/publish.sh --status        公開中のバージョンを見る
#
# 環境変数 PUBLISH_TOKEN が要る。トークンは
# https://plugins.jetbrains.com/author/me/tokens で作る。
#
#   PUBLISH_TOKEN=... scripts/publish.sh
#
# 初回だけは Marketplace の画面から手で上げる決まりで、このスクリプトでは
# 公開できない (README の「公開」を参照)。2 回目以降がこのスクリプトの担当。

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

handle_help "${BASH_SOURCE[0]}" "$@"

MARKETPLACE_API="https://plugins.jetbrains.com/api"

plugin_id() {
  sed -n 's/.*<id>\(.*\)<\/id>.*/\1/p' src/main/resources/META-INF/plugin.xml | head -1
}

# Marketplace 上の情報を JSON で返す。未公開なら空。
marketplace_plugin() {
  local id="$1" body status
  body="$(curl -sS -w '\n%{http_code}' "$MARKETPLACE_API/plugins/intellij/$id" 2>/dev/null || true)"
  status="$(printf '%s' "$body" | tail -1)"
  [ "$status" = "200" ] || return 1
  printf '%s' "$body" | sed '$d'
}

json_field() {
  python3 -c "import json,sys; print(json.load(sys.stdin).get('$1',''))" 2>/dev/null
}

# 公開済みのバージョン一覧 (新しい順)
published_versions() {
  local numeric_id="$1"
  curl -sS "$MARKETPLACE_API/plugins/$numeric_id/updates?size=20" 2>/dev/null \
    | python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)
updates = data if isinstance(data, list) else data.get('updates', [])
for u in updates:
    print(u.get('version', ''))
" 2>/dev/null
}

channel="default"
dry_run=false
status_only=false

while [ $# -gt 0 ]; do
  case "$1" in
    --channel) shift; channel="${1:-}"; [ -n "$channel" ] || die "--channel の後にチャンネル名が要ります" ;;
    --dry-run) dry_run=true ;;
    --status) status_only=true ;;
    -*) die "不明な指定です: $1 (--help で使い方)" ;;
    *) die "不明な指定です: $1 (--help で使い方)" ;;
  esac
  shift
done

id="$(plugin_id)"
[ -n "$id" ] || die "plugin.xml から id を読めませんでした"
version="$(plugin_version)"

info "プラグイン: $id"
info "手元のバージョン: $version"

command -v python3 >/dev/null 2>&1 || die "事前確認に python3 が要ります"

# --- Marketplace 側の状態を見る ---
plugin_json="$(marketplace_plugin "$id" || true)"
if [ -z "$plugin_json" ]; then
  warn "このプラグインはまだ Marketplace にありません"
  echo
  dim "初回だけは画面から手で上げる決まりになっています:"
  dim "  1. scripts/build.sh --clean"
  dim "  2. https://plugins.jetbrains.com/plugin/add に build/distributions/*.zip を上げる"
  dim "  3. 審査 (数営業日) が通ると公開ページができる"
  dim "その後、このスクリプトで更新できるようになります。"
  exit 1
fi

numeric_id="$(printf '%s' "$plugin_json" | json_field id)"
name="$(printf '%s' "$plugin_json" | json_field name)"
ok "公開中: $name (id $numeric_id)"
dim "  https://plugins.jetbrains.com/plugin/$numeric_id"

versions="$(published_versions "$numeric_id")"
latest="$(printf '%s' "$versions" | head -1)"
[ -n "$latest" ] && info "公開中の最新バージョン: $latest"

if $status_only; then
  echo
  info "公開済みのバージョン"
  printf '%s\n' "$versions" | sed 's/^/  /' | head -10
  exit 0
fi

if printf '%s\n' "$versions" | grep -qx "$version"; then
  die "バージョン $version はすでに公開されています。gradle.properties の version を上げてください (scripts/release.sh)"
fi

# --- 公開前の確認 ---
[ -n "${PUBLISH_TOKEN:-}" ] || die "PUBLISH_TOKEN が設定されていません (https://plugins.jetbrains.com/author/me/tokens)"

info "検証します"
"$GRADLE" check verifyPlugin

if $dry_run; then
  echo
  ok "事前確認は通りました"
  dim "--dry-run のため公開しません。実行するには --dry-run を外してください"
  dim "  $latest -> $version (チャンネル: $channel)"
  exit 0
fi

info "$version をチャンネル '$channel' に公開します"
if [ "$channel" = "default" ]; then
  "$GRADLE" publishPlugin
else
  "$GRADLE" publishPlugin "-PpublishChannel=$channel"
fi

ok "公開しました: https://plugins.jetbrains.com/plugin/$numeric_id"
warn "更新版も JetBrains の審査を通ってから配信されます"
