#!/usr/bin/env bash
#
# ビルド済みのプラグインを、ローカルの JetBrains IDE に入れる。
#
#   scripts/install.sh                        IDE を自動で探して入れる
#   scripts/install.sh <plugins ディレクトリ>  入れ先を指定する
#   scripts/install.sh --list                 見つかった IDE を並べる
#   scripts/install.sh --dry-run              何をするかだけ出す
#
# 入れ先は環境変数 IDE_PLUGINS_DIR でも指定できる。
# 反映には IDE の再起動が必要。

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

# この環境の JetBrains IDE の plugins ディレクトリ候補を、新しい順に並べる
find_plugins_dirs() {
  local base
  case "$(uname -s)" in
    Darwin) base="$HOME/Library/Application Support/JetBrains" ;;
    Linux)  base="$HOME/.local/share/JetBrains" ;;
    *)      return 0 ;;
  esac
  [ -d "$base" ] || return 0

  local dir
  # IntelliJIdea2025.2 / IdeaIC2025.2 のような製品ディレクトリを見る
  for dir in "$base"/IntelliJIdea* "$base"/IdeaIC*; do
    [ -d "$dir" ] || continue
    if [ "$(uname -s)" = "Darwin" ]; then
      # macOS はプラグインが plugins/ の下に入る
      printf '%s\n' "$dir/plugins"
    else
      printf '%s\n' "$dir"
    fi
  done | sort -r
}

dry_run=false
target="${IDE_PLUGINS_DIR:-}"

case "${1:-}" in
  --list)
    info "見つかった IDE"
    found=false
    while IFS= read -r dir; do
      found=true
      ok "$dir"
    done < <(find_plugins_dirs)
    $found || warn "JetBrains IDE が見つかりませんでした"
    exit 0
    ;;
  --dry-run) dry_run=true ;;
  "") ;;
  -*) die "不明な指定です: $1" ;;
  *) target="$1" ;;
esac

zip="$(latest_plugin_zip)"
if [ -z "$zip" ]; then
  info "配布用 zip が無いのでビルドします"
  "$GRADLE" buildPlugin
  zip="$(latest_plugin_zip)"
  [ -n "$zip" ] || die "zip が生成されませんでした"
fi

if [ -z "$target" ]; then
  target="$(find_plugins_dirs | head -1)"
  [ -n "$target" ] || die "IDE が見つかりませんでした。plugins ディレクトリを引数で指定してください (scripts/install.sh --list)"
  dim "入れ先を自動で選びました。別の IDE に入れるなら引数で指定してください。"
fi

# zip の中の最上位ディレクトリ名 = 入れ替える対象
plugin_dir_name="$(unzip -Z1 "$zip" | head -1 | cut -d/ -f1)"
[ -n "$plugin_dir_name" ] || die "zip の中身を読めませんでした: $zip"
destination="$target/$plugin_dir_name"

info "$zip"
info "  -> $destination"

if $dry_run; then
  dim "--dry-run のため何もしません"
  exit 0
fi

mkdir -p "$target"
if [ -d "$destination" ]; then
  dim "既存の版を置き換えます"
  rm -rf "$destination"
fi
unzip -q "$zip" -d "$target"

ok "入れました: $destination"
warn "反映するには IDE を再起動してください"
