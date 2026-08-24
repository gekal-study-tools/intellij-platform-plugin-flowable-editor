#!/usr/bin/env bash
#
# ビルド済みのプラグインを、ローカルの JetBrains IDE に入れる。
#
#   scripts/install.sh                        ビルドしてから入れる
#   scripts/install.sh --clean                クリーンビルドしてから入れる
#   scripts/install.sh --no-build             今ある zip をそのまま入れる
#   scripts/install.sh --restart              入れたあと IDE を再起動する
#   scripts/install.sh <plugins ディレクトリ>  入れ先を指定する
#   scripts/install.sh --list                 見つかった IDE を並べる
#   scripts/install.sh --dry-run              何をするかだけ出す
#
# 古い版を入れてしまう事故を防ぐため、既定で毎回ビルドする。
# 変更が無ければ Gradle が省くので、待たされることはほとんど無い。
#
# 入れ先は環境変数 IDE_PLUGINS_DIR でも指定できる。
# 反映には IDE の再起動が必要。--restart を付けると自動でやる (macOS のみ)。

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

# 再起動を待つ上限 (秒)
RESTART_TIMEOUT=90

# product-info.json から設定ディレクトリ名 (IntelliJIdea2026.2 など) を読む。
# カンマで割ってから拾うので、1 行 JSON でも整形済みでも動く。
read_data_directory_name() {
  tr ',' '\n' < "$1" \
    | sed -n 's/.*"dataDirectoryName"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
    | head -1
}

# 入れ先の plugins ディレクトリから設定ディレクトリ名を割り出す
selector_of() {
  case "$(uname -s)" in
    Darwin) basename "$(dirname "$1")" ;;
    *) basename "$1" ;;
  esac
}

# 指定した設定ディレクトリ名で動いている IDE を探し "<pid>\t<アプリ>" を返す。
# 同じ名前の別バージョンを掴まないよう、アプリ自身の申告と突き合わせる。
find_running_ide() {
  local selector="$1"
  ps -Awwo pid=,comm= | while read -r pid comm; do
    case "$comm" in
      *.app/Contents/MacOS/*) ;;
      *) continue ;;
    esac
    local app="${comm%%.app/*}.app"
    local info="$app/Contents/Resources/product-info.json"
    [ -f "$info" ] || continue
    [ "$(read_data_directory_name "$info")" = "$selector" ] || continue
    printf '%s\t%s\n' "$pid" "$app"
  done | head -1
}

# アプリ名から解決されるバンドルが、狙ったものと一致するか。
# 一致しないなら AppleScript で別バージョンを終了させかねないので手を出さない。
applescript_targets() {
  local app="$1" resolved
  resolved="$(osascript -e "get POSIX path of (path to application \"$(basename "$app" .app)\")" 2>/dev/null \
    | sed 's:/*$::')"
  [ "$resolved" = "$app" ]
}

# IDE を終了させ、消えるまで待つ
quit_ide() {
  local pid="$1" app="$2" waited=0
  osascript -e "tell application \"$(basename "$app" .app)\" to quit" >/dev/null 2>&1 || true
  while kill -0 "$pid" 2>/dev/null; do
    if [ "$waited" -ge "$RESTART_TIMEOUT" ]; then
      return 1
    fi
    sleep 1
    waited=$((waited + 1))
  done
  return 0
}

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
build=true
clean_build=false
restart=false
target="${IDE_PLUGINS_DIR:-}"

while [ $# -gt 0 ]; do
  case "$1" in
    --help|-h) print_usage "${BASH_SOURCE[0]}"; exit 0 ;;
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
    # --build は既定の動作。明示されても素直に受ける。
    --build|-b) build=true ;;
    --no-build) build=false ;;
    --clean) clean_build=true; build=true ;;
    --restart) restart=true ;;
    --dry-run) dry_run=true ;;
    -*) die "不明な指定です: $1 (--help で使い方)" ;;
    *) target="$1" ;;
  esac
  shift
done

zip="$(latest_plugin_zip)"

if $build && ! $dry_run; then
  if $clean_build; then
    info "クリーンビルドします"
    "$GRADLE" clean buildPlugin
  else
    info "ビルドします (変更が無ければ Gradle が省きます)"
    "$GRADLE" buildPlugin
  fi
  zip="$(latest_plugin_zip)"
elif $build && $dry_run; then
  dim "--dry-run のためビルドしません"
elif [ -z "$zip" ]; then
  die "zip がありません。--no-build を外してください"
fi

[ -n "$zip" ] || die "zip が生成されませんでした"

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

# 再起動するなら、入れる前に閉じておくほうが確実
running_pid=""
running_app=""
if $restart; then
  if [ "$(uname -s)" != "Darwin" ]; then
    warn "--restart は macOS でのみ対応しています。手動で再起動してください"
    restart=false
  else
    selector="$(selector_of "$target")"
    entry="$(find_running_ide "$selector" || true)"
    if [ -z "$entry" ]; then
      dim "$selector で動いている IDE は見つかりませんでした (再起動は不要)"
      restart=false
    else
      running_pid="${entry%%	*}"
      running_app="${entry#*	}"
      if ! applescript_targets "$running_app"; then
        warn "同名の別バージョンがある可能性があるため、自動再起動は見送ります: $running_app"
        restart=false
      fi
    fi
  fi
fi

if $dry_run; then
  $restart && dim "--dry-run のため再起動しません (本来なら $running_app を再起動します)"
  dim "--dry-run のため何もしません"
  exit 0
fi

if $restart; then
  info "IDE を終了します: $running_app (pid $running_pid)"
  if quit_ide "$running_pid" "$running_app"; then
    ok "終了しました"
  else
    warn "${RESTART_TIMEOUT} 秒待っても終了しませんでした (未保存の確認などが出ているかもしれません)"
    warn "プラグインは入れますが、再起動は手動でお願いします"
    restart=false
  fi
fi

mkdir -p "$target"
if [ -d "$destination" ]; then
  dim "既存の版を置き換えます"
  rm -rf "$destination"
fi
unzip -q "$zip" -d "$target"

ok "入れました: $destination"

if $restart; then
  info "IDE を起動します"
  open -a "$running_app"
  ok "再起動しました"
else
  warn "反映するには IDE を再起動してください"
fi
