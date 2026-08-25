# Flowable BPMN Editor

![Build](https://github.com/gekal-study-tools/intellij-platform-plugin-flowable-editor/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/33780.svg)](https://plugins.jetbrains.com/plugin/33780)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/33780.svg)](https://plugins.jetbrains.com/plugin/33780)

<!-- Plugin description -->
Edit [Flowable](https://www.flowable.com/) BPMN 2.0 process definitions inside IntelliJ IDEA.

The plugin opens `*.bpmn`, `*.bpmn20.xml` and any XML file in the BPMN 2.0 namespace in a split
editor: the XML on the left, a live diagram on the right. Clicking a shape moves the caret to the
matching element, and moving the caret highlights the shape. Definitions without `BPMNDI` diagram
information are laid out automatically, so hand-written Flowable definitions render too.

On top of the preview it adds BPMN-aware editing support:

- Bundled BPMN 2.0 and Flowable extension schemas, so tag and attribute completion and schema
  validation work offline.
- Reference resolution and completion for `sourceRef`, `targetRef`, `attachedToRef`, `default`,
  `errorRef`, `signalRef`, `messageRef` and friends.
- A BPMN structure view that shows processes and their flow elements.
- Inspections for unresolved references, duplicate ids, processes without a start or end event,
  disconnected flow elements, service and user tasks missing their Flowable configuration, and
  elements without an id (with a quick fix that generates one).
- A "Flowable BPMN Process" file template in the New menu.

The diagram is editable. Drag shapes to move them, drag a corner to resize, drag from the
connection handle to draw a sequence flow, pick elements from the palette to add them, and
press Delete to remove one. Every change is written straight back to the XML, so the file
stays the single source of truth and IDE undo works as usual. Definitions without `BPMNDI`
get their diagram information written out on the first edit.

Only BPMN is supported - DMN, CMMN and Flowable Form files are out of scope.

<em>This is an unofficial, community-maintained plugin. It is not affiliated with,
endorsed by, or supported by Flowable AG. "Flowable" is a trademark of Flowable AG
and is used here only to describe what the plugin works with.</em>
<!-- Plugin description end -->

## できること

| 機能 | 説明 |
| --- | --- |
| 分割エディタ | 左に XML、右に図。図形クリック ⇄ キャレット移動が双方向に同期する |
| 図の編集 | 移動・大きさ変更・接続・追加・削除・改名。変更は XML に書き戻り、Undo も効く |
| 自動レイアウト | `bpmndi:BPMNDiagram` を持たない定義でも、シーケンスフローから左→右に階層配置して描画する |
| 図の操作 | トラックパッドのピンチで拡大縮小、2 本指スクロールで移動。詳細は下記 |
| スキーマ補完・検証 | BPMN 2.0 / Flowable 拡張の XSD を同梱。ネットワーク不要 |
| 参照解決 | `targetRef="approveTask"` から `id="approveTask"` へ Ctrl+クリック。補完も効く |
| 構造ビュー | プロセスとフロー要素をツリー表示（シーケンスフローは図で見るほうが分かりやすいので省略） |
| 検査 | 未解決参照 / id 重複 / 開始・終了イベント欠落 / 未接続要素 / タスク設定漏れ / id 欠落 |

## 図の操作

macOS のトラックパッドで、他のアプリと同じ感覚で扱えるようにしてある。

| 操作 | 動作 |
| --- | --- |
| ピンチイン / ピンチアウト | 拡大・縮小（指の位置が動かないよう追従する） |
| 2 本指スクロール（上下・左右） | 図の移動 |
| Cmd + スクロール（Windows / Linux は Ctrl） | 拡大・縮小。トラックパッドではなめらかに、マウスのホイールは 1 段ずつ |
| 何もない場所をドラッグ | 図の移動 |
| 図形をクリック | 選択し、XML の該当箇所にキャレットを移す |
| 図形をドラッグ | 移動する |
| 角のつまみをドラッグ | 大きさを変える |
| 右の丸をドラッグ（または Shift + ドラッグ） | 別の図形へ線を引く |
| パレットで選んでキャンバスを押す | その位置に要素を置く |
| Esc | 構えている道具を外して選択に戻る |
| Delete / Backspace | 選択中の要素を消す |
| ダブルクリック | 名前を編集する |
| ツールバーの整列 | 図を左から右へ並べ直す |
| 線を選んで折れ点をドラッグ | 線の形を変える |
| 線分の中ほどの薄い印をドラッグ | 折れ点を足す |
| 折れ点をダブルクリック | 折れ点を消す |

図は開いたときにウィンドウの**中央**に置かれる。図のまわりにはウィンドウ 1 枚分の
余白があるので、**上下左右どちらにも行き止まりなく動かせる**（図を画面の外へ
送り出しきれる）。ツールバーの「ウィンドウに合わせる」でいつでも中央に戻せる。

背景には薄い**点線の方眼**を敷いてある。何も無いところを動かしても、
どれだけ動いたか・今どのくらいの拡大率かが目で追える。方眼の間隔は拡大率に応じて
2 の冪で切り替わるので、詰まりすぎたり空きすぎたりしない。PNG 書き出しには入らない。

ツールバーには拡大・縮小・ウィンドウに合わせる・等倍・PNG 書き出し・再読み込みを置いてある。
拡大・縮小はいま見ている中心を保ったまま働く。

## 図の編集について

変更はすべて PSI 経由で XML に書き戻される。図が状態を抱え込むことはないので、
**XML が常に唯一の正**であり、IDE の Undo / Redo がそのまま効く。

消すときはぶら下がるものも一緒に片付ける。要素を消すと、それに触れる
シーケンスフロー・張り付いた境界イベント・対応する図形情報 (BPMNDI) も消える。
半端に残って壊れた定義にならないようにするため。

`BPMNDI` を持たない定義は、**最初の編集時に自動レイアウトの座標がそのまま
図形情報として書き出される**。以降の移動は単なる座標の更新になる。

読み取り専用のファイルでは編集操作は働かない。

### 線の形

線を選ぶと折れ点につまみが出る。既にある折れ点は塗りつぶした丸、
線分の中ほどには薄い丸が出て、薄いほうを掴むとそこに折れ点が差し込まれる。
両端は図形の縁に吸い付かせているので掴めない。図形を動かせば自動で付き直る。

### 区画を動かすと中身も動く

プール・レーン・サブプロセスを動かすと、**中に入っている要素も一緒に動く**。
アクティビティを動かせば、張り付いた境界イベントも付いてくる。
大きさを変えただけのときは中身は動かない。

### プールとレーンの作成

パレットからプールを置くと、BPMN の約束どおり `collaboration` の `participant`
として作られる。最初のプールは既にあるプロセスを指し、2 つ目からは新しい空の
プロセスを起こす。図の面 (`BPMNPlane`) も `collaboration` を指すよう付け替える。

レーンはプロセスの `laneSet` に入る。プールの上に落とせばそのプールのプロセスへ、
そうでなければ最初のプロセスへ足す。`laneSet` が無ければ作る。

### 線の種類は繋ぐ相手で決まる

線を引くとき、種類は自動で選ばれる。取り違えると BPMN の意味が変わるため。

| 繋ぐ相手 | できる線 |
| --- | --- |
| どちらかがテキスト注記 | 関連 (`association`) |
| 別々のプールにまたがる | メッセージフロー (`messageFlow`) |
| それ以外 | シーケンスフロー (`sequenceFlow`) |

### 所属は図に追従する

図で動かした結果は、定義の側にも反映される。図では移ったのに定義では元のまま、
という食い違いを残さないため。**Flowable が見るのは定義のほう**なので、
見た目だけ合っている状態がいちばん厄介になる。

| 動かした先 | 書き換わるもの |
| --- | --- |
| 別のレーン | `flowNodeRef` |
| どのレーンでもない場所 | `flowNodeRef` から外れる |
| 別のプール | 要素がそのプールの `process` へ移る |

プールをまたいだ結果、プロセスをまたぐことになったシーケンスフローは
**メッセージフローに直す**。プールをまたぐシーケンスフローは BPMN では作れない。
同じプロセスに戻ればシーケンスフローに戻る。id は変えないので、
線の図形情報 (`BPMNEdge`) はそのまま使われる。

### 図の整列

ツールバーの整列を押すと、いまの座標を捨てて左から右へ並べ直し、線も引き直す。
1 つの取り消し単位なので、気に入らなければ一度で元に戻せる。

レーンがある図では、横の並び（流れの順）を作り直したうえで、
**縦だけを元居たレーンの帯に戻す**。帯に収まらない要素があれば帯のほうを広げ、
下の帯を押し下げ、プールを覆う大きさにする。要素を区画の外に出すよりは、
区画が伸びるほうが定義として素直なため。

### パレット

要素はイベント・アクティビティ・ゲートウェイの 3 組に分けて並べ、
組の間に細い区切り線を入れてある。先頭は選択の道具で、Esc でもここに戻る。
構えたまま抜け出せない状態を作らないため。

| 組 | 置ける要素 |
| --- | --- |
| イベント | 開始 / 終了 / タイマー / メッセージ / 境界タイマー |
| アクティビティ | ユーザー / サービス / スクリプト / ビジネスルール / 受信 / 呼び出し / サブプロセス / テキスト注記 |
| ゲートウェイ | 排他 / 並列 / 包含 / イベント |
| プールとレーン | プール / レーン |

**境界イベントだけは貼り付け先が要る。** アクティビティの上で押すとその下辺に付き、
`attachedToRef` が書かれる。何も無いところで押しても置かれず、道具は構えたまま残るので
狙い直せる。貼り付け先を消すと、付いていた境界イベントも一緒に消える。

アイコンは要素ごとに描き起こしてある。図の描画をそのまま縮めると、
タスクの種別マーカーが潰れて開始と終了、ユーザーとサービスの区別が付かなくなるため、
16px で読める形に作り直した。**構造ビューも同じアイコンを使う**ので、
木と図で同じ要素が同じ絵に見える。

## 対応ファイル

- `*.bpmn`
- `*.bpmn20.xml`
- `*.bpmn.xml`
- 上記以外の `*.xml` でも、先頭に BPMN 2.0 名前空間（`http://www.omg.org/spec/BPMN/20100524/MODEL`）が
  含まれていればプレビュー・検査の対象になる

## 開発

[IntelliJ Platform Plugin Template][template] をベースにしている。
よく使う操作は `scripts/` にまとめてあるので、基本はこちらを使う。

| スクリプト | 用途 |
| --- | --- |
| `scripts/build.sh` | 配布用 zip をビルドする（`--clean` でクリーンビルド） |
| `scripts/test.sh` | テストを実行する（`scripts/test.sh BpmnModelParserTest` で絞り込み） |
| `scripts/lint.sh` | ktlint でコードスタイルを検査する（`--fix` で自動修正） |
| `scripts/verify.sh` | Plugin Verifier をかけ、IDE ごとの判定を一覧表示する |
| `scripts/run-ide.sh` | サンドボックス IDE を起動してプラグインを試す |
| `scripts/install.sh` | ビルドしてローカルの JetBrains IDE に入れる（`--restart` で IDE 再起動まで） |
| `scripts/render.sh` | テスト用 BPMN を PNG に描き出して目視確認する |
| `scripts/release.sh` | 版を上げて CHANGELOG を確定し、コミットする |
| `scripts/publish.sh` | Marketplace に更新版を公開する（`--status` で公開中の版を確認） |

```bash
scripts/test.sh                 # テスト
scripts/lint.sh --fix           # 整形
scripts/run-ide.sh              # サンドボックスで動かす

scripts/install.sh --list       # 手元の IDE を探す
scripts/install.sh              # ビルドして、いちばん新しい IDE に入れる
scripts/install.sh --restart    # 入れたあと IDE も再起動する
scripts/install.sh --clean      # クリーンビルドしてから入れる
scripts/install.sh --no-build   # 今ある zip をそのまま入れる
scripts/install.sh "$HOME/Library/Application Support/JetBrains/IntelliJIdea2025.2/plugins"
```

どのスクリプトも `--help` で使い方が出る。

`install.sh` は古い版を入れてしまう事故を防ぐため、既定で毎回ビルドする
（変更が無ければ Gradle が省くので、ほぼ待たされない）。
`--restart` は macOS 専用で、`product-info.json` の設定ディレクトリ名を見て
入れ先に対応する IDE だけを終了・再起動する。未保存の確認ダイアログなどで
終了できなかった場合は、プラグインだけ入れて手動再起動を促す。

素の Gradle でも同じことができる（`./gradlew buildPlugin check verifyPlugin runIde`）。
`.run/` にある実行構成（Run Plugin / Run Tests / Run Verifications）もそのまま使える。

### コードスタイル

ktlint を Gradle から直接呼んでいる（サードパーティのプラグインは挟んでいない）。
`./gradlew check` に組み込んであるので CI でも検査される。
規則は `.editorconfig` の `[*.{kt,kts}]` セクションで調整する。

### 構成

```
src/main/kotlin/com/github/gekal/flowableeditor/
├── bpmn/        ファイルタイプ・名前空間・BPMN ファイル判定
├── model/       図のモデル、XML → モデルのパーサ、自動レイアウト、線のルーティング
├── editor/      分割エディタ、プレビュー、キャンバス、描画
├── psi/         id の索引と参照
├── inspection/  検査とクイックフィックス
├── structure/   構造ビュー
├── schema/      同梱 XSD の登録
└── actions/     New メニューのアクション

scripts/         開発用のシェルスクリプト
src/test/testData/  検査・描画テスト用の BPMN 定義
```

## 公開 (JetBrains Marketplace)

初回だけは Marketplace の画面から手で上げる決まりになっている。
2 回目以降は GitHub Actions が自動で公開する。

### 1. 初回アップロード（済み）

登録済み: **[plugins.jetbrains.com/plugin/33780](https://plugins.jetbrains.com/plugin/33780)**（xmlId `com.github.gekal.flowableeditor`）

以下は初回に実際に通った手順。次に別のプラグインを出すときのために残しておく。

```bash
scripts/build.sh --clean          # build/distributions/*.zip ができる
```

1. [plugins.jetbrains.com/plugin/add](https://plugins.jetbrains.com/plugin/add) を開く
2. zip をアップロードし、ライセンスに **Apache 2.0**、タグに **Editor** を選ぶ
3. 送信すると JetBrains の審査に入る（数営業日）。承認されるまで公開ページは出ない

**つまずいた点（重要）**

- **Source code URL はオープンソースライセンスを選ぶと必須**になる。
  そのためリポジトリは public でなければならない（private だと審査でも 404 になる）。
- **Vendor Status を先にベンダー側（`/vendor/<id>/edit`）で確定しておく必要がある。**
  未設定のまま（`Vendor Status not provided`）だとアップロードが
  「郵便番号 / 住所 / 市区町村 / 電話番号の形式が不正」という 4 つのエラーで弾かれる。
  アップロード画面で Non-trader を選ぶだけでは解消しない。
  Trader を選んで住所・電話を正しい書式で埋めると通った。
  書式は半角のみで、電話番号は**ハイフン不可**（数字・空白・先頭の `+` のみ）、
  市区町村は大文字始まり。
- **Trader を選ぶと氏名・メール・住所・電話が利用者に表示される。**
  公開範囲を絞りたい場合は、レコードが正しく埋まった後で Non-trader に
  戻せるか試すとよい。

審査では第三者製品名の扱いが見られる。プラグインの説明文には
「非公式であり Flowable AG とは無関係」「Flowable は Flowable AG の商標」を明記してある。
同梱している XSD の出典と Apache 2.0 表示は
[`src/main/resources/META-INF/third-party-notices.md`](src/main/resources/META-INF/third-party-notices.md)。

### 2. トークンを GitHub に登録する

1. [Marketplace のトークン発行ページ](https://plugins.jetbrains.com/author/me/tokens) で
   Permanent Token を作る
2. リポジトリの Settings → Secrets and variables → Actions に `PUBLISH_TOKEN` として入れる

署名（任意だが推奨）まで行うなら、[Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)
の手順で証明書を作り、`CERTIFICATE_CHAIN` / `PRIVATE_KEY` / `PRIVATE_KEY_PASSWORD` も登録する。
`publishPlugin` はこれらの環境変数を既定で読むので、`build.gradle.kts` 側の設定は要らない。

### 3. 2 回目以降（更新）

**GitHub 経由（推奨）**

```bash
scripts/release.sh 0.2.0 --push
```

push すると Build ワークフローが下書きリリースを作る。
GitHub でそれを publish すると Release ワークフローが走り、`publishPlugin` が
Marketplace へ上げる。

**手元から直接**

```bash
scripts/publish.sh --status                      # 公開中の版を見る
PUBLISH_TOKEN=... scripts/publish.sh --dry-run   # 事前確認だけ
PUBLISH_TOKEN=... scripts/publish.sh             # 安定版として公開する
PUBLISH_TOKEN=... scripts/publish.sh --channel eap   # 事前公開チャンネルへ
```

### 事前公開チャンネル（EAP）と版の採番

Marketplace は**同じ版を上げ直せない**。EAP のように同じ内容を何度も出す
チャンネルでは、素の版のままだと 2 回目から必ず失敗する。

そのため `--channel` を付けたときは、公開済みの一覧を見て
**ビルド番号を自動で進めた版**を組み立てる。

```
gradle.properties の version = 0.2.0

1 回目: scripts/publish.sh --channel eap  ->  0.2.0-eap.1
2 回目: scripts/publish.sh --channel eap  ->  0.2.0-eap.2
3 回目: scripts/publish.sh --channel eap  ->  0.2.0-eap.3
```

番号は Marketplace の公開済みバージョンから次の空き番号を選ぶので、
手元の状態に依存せず、続けて実行しても衝突しない。
チャンネル名はそのまま使われるので `--channel beta` なら `0.2.0-beta.1` になる。

版は `-PpluginVersion` で Gradle に渡され、zip の名前と `plugin.xml` の
`<version>` の両方に反映される。手動で組み立てたいときは同じように渡せる。

```bash
./gradlew buildPlugin -PpluginVersion=0.2.0-eap.3
```

素の版（`0.2.0`）がすでに公開されている状態で EAP を出そうとすると警告する。
`0.2.0-eap.1` は版の比較では `0.2.0` より**前**として扱われるため、
次の版に向けた事前公開なら先に `gradle.properties` の `version` を上げておく。
更新履歴は素の版で引くので、EAP には `Unreleased` の内容が載る。

公開前に次を自動で確かめる。

- Marketplace に登録済みか（未登録なら初回アップロードの手順を案内して止まる）
- 手元の版がすでに公開されていないか（重複アップロードは弾かれるため）
- `PUBLISH_TOKEN` があるか
- `check` と `verifyPlugin` が通るか

更新版も JetBrains の審査を通ってから配信される。

## 制限

- 整列は要素の大きさも決め直す。手で広げたサブプロセスの大きさは保たれない
- 折れ点を手で置いた線も、整列では引き直される
- 対応するのは BPMN のみ。DMN / CMMN / Flowable Form は対象外
- 参照は同一ファイル内で解決する。`calledElement` のような他ファイルのプロセスキーは追わない

## ライセンスと同梱物

プラグイン本体は [Apache License 2.0](LICENSE)（Copyright 2026 gekal）。

同梱している XSD が Apache 2.0 なので、本体も同じライセンスに揃えてある。
Apache 2.0 は特許条項を含むため、BPMN のような仕様を実装するものと相性がよい。
Marketplace の登録画面でも **Apache 2.0** を選ぶこと。

`src/main/resources/schemas/bpmn/` の XSD は Flowable の `flowable-bpmn-converter`
（Apache License 2.0）に同梱されているものと同一で、BPMN 2.0 仕様に由来する。

---

Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
