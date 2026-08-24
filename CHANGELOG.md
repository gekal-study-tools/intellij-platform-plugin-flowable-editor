<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Flowable BPMN Editor Changelog

## [Unreleased]

### Added

- Split editor for `*.bpmn`, `*.bpmn20.xml` and any XML file in the BPMN 2.0 namespace: XML on the left, a live diagram on the right, with clicks and caret moves synchronised both ways.
  <br/>`*.bpmn` / `*.bpmn20.xml`、および BPMN 2.0 名前空間を含む `*.xml` を分割エディタで開く。左が XML、右が図で、図形のクリックとキャレット移動が双方向に同期する。
- Automatic layout for definitions without `BPMNDI` diagram information, so hand-written Flowable definitions render too.
  <br/>`BPMNDI` を持たない定義でも、シーケンスフローから左から右へ自動配置して描画する。手書きの定義もそのまま図になる。
- Trackpad gestures on macOS: pinch to zoom, two-finger scroll to move. Cmd/Ctrl with the wheel zooms smoothly on a trackpad and one notch at a time with a mouse.
  <br/>macOS のトラックパッド操作。ピンチで拡大縮小、2 本指スクロールで移動。Cmd/Ctrl + スクロールはトラックパッドではなめらかに、マウスでは 1 段ずつ拡大縮小する。
- Diagram toolbar: zoom in and out, fit to window, actual size, export as PNG, and reload.
  <br/>図のツールバー。拡大・縮小・ウィンドウに合わせる・等倍・PNG 書き出し・再読み込み。
- Bundled BPMN 2.0 and Flowable extension schemas, so tag and attribute completion and schema validation work offline.
  <br/>BPMN 2.0 と Flowable 拡張の XSD を同梱。ネットワークに出ずにタグ・属性の補完とスキーマ検証が効く。
- Reference resolution and completion for `sourceRef`, `targetRef`, `attachedToRef`, `default`, `errorRef`, `signalRef`, `messageRef` and friends.
  <br/>`sourceRef` / `targetRef` / `attachedToRef` / `default` / `errorRef` / `signalRef` / `messageRef` などから `id` 宣言への参照解決と補完。
- BPMN structure view showing processes and their flow elements.
  <br/>プロセスとフロー要素を並べる BPMN 向けの構造ビュー。
- Six inspections: unresolved references, duplicate ids, processes without a start or end event, disconnected flow elements, service and user tasks missing their Flowable configuration, and elements without an id (with a quick fix that generates one).
  <br/>検査 6 種。未解決参照、id 重複、開始・終了イベントの欠落、未接続要素、タスクの設定漏れ、id 欠落（id 欠落には自動生成のクイックフィックス付き）。
- "Flowable BPMN Process" file template in the New menu.
  <br/>「New」メニューからのプロセス定義テンプレート生成。
