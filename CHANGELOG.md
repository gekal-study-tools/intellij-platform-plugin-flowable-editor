<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Flowable BPMN Editor Changelog

## [Unreleased]

### Added

- `*.bpmn` / `*.bpmn20.xml`、および BPMN 名前空間を含む `*.xml` を認識する独自ファイルタイプ
- XML エディタと図プレビューの分割エディタ。クリックとキャレット移動が双方向に同期する
- BPMNDI が無い定義ファイル向けの自動レイアウト（左から右への階層配置）
- 図の拡大縮小・ウィンドウに合わせる・PNG 書き出し
- BPMN 2.0 と Flowable 拡張の XSD を同梱し、オフラインで補完とスキーマ検証を提供
- `sourceRef` / `targetRef` などから `id` 宣言への参照解決と補完
- BPMN 向けの構造ビュー
- 検査 6 種（未解決参照、id 重複、開始・終了イベントの欠落、未接続要素、タスク設定漏れ、id 欠落）
- 「New」メニューからのプロセス定義テンプレート生成
- 開発用スクリプト `scripts/`（ビルド・テスト・整形・検証・サンドボックス起動・IDE への導入・描画確認・リリース）
- ktlint によるコードスタイル検査。`./gradlew check` に組み込み、規則は `.editorconfig` で管理する
