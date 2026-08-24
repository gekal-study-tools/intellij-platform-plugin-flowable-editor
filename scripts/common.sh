#!/usr/bin/env bash
# 各スクリプトから読み込む共通処理。単体では実行しない。

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

GRADLE="./gradlew"

# 端末に出すときだけ色を付ける (CI のログを汚さない)
if [ -t 1 ]; then
  C_INFO=$'\033[36m'; C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_ERR=$'\033[31m'; C_DIM=$'\033[2m'; C_OFF=$'\033[0m'
else
  C_INFO=''; C_OK=''; C_WARN=''; C_ERR=''; C_DIM=''; C_OFF=''
fi

info() { printf '%s==>%s %s\n' "$C_INFO" "$C_OFF" "$*"; }
ok()   { printf '%s[OK]%s %s\n' "$C_OK" "$C_OFF" "$*"; }
warn() { printf '%s[!]%s %s\n' "$C_WARN" "$C_OFF" "$*" >&2; }
dim()  { printf '%s%s%s\n' "$C_DIM" "$*" "$C_OFF"; }
die()  { printf '%s[NG]%s %s\n' "$C_ERR" "$C_OFF" "$*" >&2; exit 1; }

# gradle.properties から値を 1 つ取り出す
gradle_property() {
  sed -n "s/^$1[[:space:]]*=[[:space:]]*//p" gradle.properties | head -1
}

plugin_version() { gradle_property version; }

# 直近にビルドされた配布用 zip のパス。無ければ空文字を返す。
latest_plugin_zip() {
  ls -t build/distributions/*.zip 2>/dev/null | head -1 || true
}

# OS ごとのファイルオープンコマンド
open_path() {
  if command -v open >/dev/null 2>&1; then
    open "$1"
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$1" >/dev/null 2>&1
  else
    dim "開けませんでした。手動で確認してください: $1"
  fi
}
