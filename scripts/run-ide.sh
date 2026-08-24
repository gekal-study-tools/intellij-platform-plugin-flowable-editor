#!/usr/bin/env bash
#
# サンドボックスの IntelliJ IDEA を起動してプラグインを試す。
#
#   scripts/run-ide.sh
#
# サンドボックスは .intellijPlatform/sandbox/ に作られる。
# IDE のログは .intellijPlatform/sandbox/*/*/log/idea.log。

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

handle_help "${BASH_SOURCE[0]}" "$@"

info "サンドボックス IDE を起動します (終了するまで戻りません)"
dim "ログ: .intellijPlatform/sandbox/*/*/log/idea.log"
"$GRADLE" runIde "$@"
