# Flowable BPMN Editor

![Build](https://github.com/gekal-study-tools/intellij-platform-plugin-flowable-editor/workflows/Build/badge.svg)

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
<!-- Plugin description end -->

## できること

| 機能 | 説明 |
| --- | --- |
| 分割エディタ | 左に XML、右に図。図形クリック ⇄ キャレット移動が双方向に同期する |
| 自動レイアウト | `bpmndi:BPMNDiagram` を持たない定義でも、シーケンスフローから左→右に階層配置して描画する |
| 図の操作 | 拡大縮小（Ctrl/Cmd + ホイール）、ウィンドウに合わせる、等倍、PNG 書き出し、空白部分のドラッグでパン |
| スキーマ補完・検証 | BPMN 2.0 / Flowable 拡張の XSD を同梱。ネットワーク不要 |
| 参照解決 | `targetRef="approveTask"` から `id="approveTask"` へ Ctrl+クリック。補完も効く |
| 構造ビュー | プロセスとフロー要素をツリー表示（シーケンスフローは図で見るほうが分かりやすいので省略） |
| 検査 | 未解決参照 / id 重複 / 開始・終了イベント欠落 / 未接続要素 / タスク設定漏れ / id 欠落 |

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
| `scripts/install.sh` | ビルドした zip をローカルの JetBrains IDE に入れる |
| `scripts/render.sh` | テスト用 BPMN を PNG に描き出して目視確認する |
| `scripts/release.sh` | 版を上げて CHANGELOG を確定し、コミットする |

```bash
scripts/test.sh                 # テスト
scripts/lint.sh --fix           # 整形
scripts/run-ide.sh              # サンドボックスで動かす

scripts/install.sh --list       # 手元の IDE を探す
scripts/install.sh              # いちばん新しい IDE に入れる（要 IDE 再起動）
scripts/install.sh "$HOME/Library/Application Support/JetBrains/IntelliJIdea2025.2/plugins"
```

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

## 制限

- プレビューは読み取り専用。図形のドラッグによる編集は行わない（編集は XML 側で行う）
- 対応するのは BPMN のみ。DMN / CMMN / Flowable Form は対象外
- 参照は同一ファイル内で解決する。`calledElement` のような他ファイルのプロセスキーは追わない

## ライセンスと同梱物

プラグイン本体は [Apache License 2.0](LICENSE)。

`src/main/resources/schemas/bpmn/` の XSD は Flowable の `flowable-bpmn-converter`
（Apache License 2.0）に同梱されているものと同一で、BPMN 2.0 仕様に由来する。

---

Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
