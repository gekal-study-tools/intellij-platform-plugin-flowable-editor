<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Flowable BPMN Editor Changelog

## [Unreleased]

## [0.1.0] - 2026-08-25

### Added

- The diagram is editable: move and resize shapes, draw sequence flows, add elements from a palette, delete, and rename. Changes are written back to the XML, so the file stays the source of truth and IDE undo works. Definitions without `BPMNDI` get their diagram information written out on the first edit.
  <br/>図を編集できる。移動・大きさ変更・接続線の作成・パレットからの追加・削除・改名に対応し、変更は XML に書き戻る。XML が唯一の正なので Undo もそのまま効く。`BPMNDI` を持たない定義は最初の編集で図形情報が書き出される。
- The palette offers 16 elements grouped by events, activities and gateways, each with its own icon and a pointer tool (Escape also disarms). Boundary events attach to the activity they are dropped on. The structure view uses the same icons.
  <br/>パレットは 16 種類をイベント・アクティビティ・ゲートウェイに分けて並べ、要素ごとに専用のアイコンを持つ。選択の道具があり Esc でも解除できる。境界イベントは落とした先のアクティビティに貼り付く。構造ビューも同じアイコンを使う。
- Connection shape can be edited: drag a bend point to move it, drag the middle of a segment to add one, double-click to remove it. The ends stay docked to the shapes.
  <br/>線の形を編集できる。折れ点をドラッグして動かし、線分の中ほどを掴むと折れ点が増え、ダブルクリックで消える。両端は図形の縁に吸い付いたまま。
- Automatic arrangement from the toolbar: lays the diagram out left to right and redraws the connections, as one undoable step. Lanes are respected - elements stay in the lane they were in, lane bands grow to fit their contents, and containers grow rather than letting contents spill out.
  <br/>ツールバーからの自動整列。左から右へ並べ直して線も引き直す。取り消しは一度で効く。レーンは尊重され、要素は元居た帯に留まり、帯は中身に合わせて広がり、はみ出す場合は区画のほうが広がる。
- Membership follows the diagram: moving an element into another lane rewrites `flowNodeRef`, and moving it into another pool moves it to that pool's process. A sequence flow that ends up crossing pools becomes a message flow, and turns back when it no longer does.
  <br/>所属が図に追従する。別のレーンへ動かすと `flowNodeRef` が、別のプールへ動かすとそのプールのプロセスへ要素が移る。プールをまたぐことになったシーケンスフローはメッセージフローに直り、またがなくなれば戻る。
- Moving a pool, lane or sub process carries everything inside it. Boundary events stay docked to their host when it is moved or resized. Deleting a pool removes the process it points at.
  <br/>プール・レーン・サブプロセスを動かすと中身が付いてくる。境界イベントは貼り付け先を動かしても大きさを変えても縁に付いたまま。プールを消すと指していたプロセスも消える。
- A pool created around an existing process is sized to enclose it.
  <br/>既にあるプロセスを包むプールは、その要素を囲む大きさで作られる。
- Pools and lanes can be created from the palette, and the connection type (sequence flow, message flow, association) is chosen from what is being connected.
  <br/>プールとレーンをパレットから作れる。線の種類は繋ぐ相手から自動で決まる（シーケンスフロー / メッセージフロー / 関連）。
- Split editor for `*.bpmn`, `*.bpmn20.xml` and any XML file in the BPMN 2.0 namespace: XML on the left, a live diagram on the right, with clicks and caret moves synchronised both ways.
  <br/>`*.bpmn` / `*.bpmn20.xml`、および BPMN 2.0 名前空間を含む `*.xml` を分割エディタで開く。左が XML、右が図で、図形のクリックとキャレット移動が双方向に同期する。
- Automatic layout for definitions without `BPMNDI` diagram information, so hand-written Flowable definitions render too.
  <br/>`BPMNDI` を持たない定義でも、シーケンスフローから左から右へ自動配置して描画する。手書きの定義もそのまま図になる。
- Trackpad gestures on macOS: pinch to zoom, two-finger scroll to move. Cmd/Ctrl with the wheel zooms smoothly on a trackpad and one notch at a time with a mouse.
  <br/>macOS のトラックパッド操作。ピンチで拡大縮小、2 本指スクロールで移動。Cmd/Ctrl + スクロールはトラックパッドではなめらかに、マウスでは 1 段ずつ拡大縮小する。
- Diagram toolbar: zoom in and out, fit to window, actual size, export as PNG, and reload. Zooming keeps the centre of the current view in place.
  <br/>図のツールバー。拡大・縮小・ウィンドウに合わせる・等倍・PNG 書き出し・再読み込み。拡大縮小はいま見ている中心を保つ。
- The diagram opens centred in the window, and can be panned without hitting an edge in any direction.
  <br/>図は開いたときにウィンドウの中央に置かれ、上下左右どちらにも行き止まりなく動かせる。
- Dotted grid background that adapts its spacing to the zoom level, so panning and zooming stay easy to follow. It is not included in the PNG export.
  <br/>拡大率に応じて間隔が変わる点線の方眼を背景に敷いた。移動量と拡大率が目で追える。PNG 書き出しには入らない。
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
- Pre-release channel publishing allocates a build number automatically (`0.2.0-eap.1`, `-eap.2`, ...), so the same version is never uploaded twice.
  <br/>事前公開チャンネルへの公開でビルド番号を自動採番する（`0.2.0-eap.1`, `-eap.2` ...）。同じ版を二度上げてしまうことがなくなる。

[Unreleased]: https://github.com/gekal-study-tools/intellij-platform-plugin-flowable-editor/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/gekal-study-tools/intellij-platform-plugin-flowable-editor/commits/v0.1.0
