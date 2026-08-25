package com.github.gekal.flowableeditor.editor

import com.github.gekal.flowableeditor.FlowableBundle
import com.github.gekal.flowableeditor.edit.BpmnPaletteItem
import com.github.gekal.flowableeditor.model.BpmnAutoLayout
import com.github.gekal.flowableeditor.model.BpmnBounds
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnEdge
import com.github.gekal.flowableeditor.model.BpmnGeometry
import com.github.gekal.flowableeditor.model.BpmnNode
import com.github.gekal.flowableeditor.model.BpmnPoint
import com.intellij.ui.ClientProperty
import com.intellij.ui.components.Magnificator
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import java.awt.BasicStroke
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.KeyStroke
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * BPMN 図を表示するキャンバス。
 *
 * 図のまわりにはビューポート 1 枚分の余白を持たせてあり、図を画面外まで
 * 送り出せる。どの向きにも行き止まりが無いので、広い図でも見たい場所へ素直に
 * 動かせる。図そのものは既定でビューポートの中央に置く。
 *
 * - 図形クリックで選択し、[onElementSelected] でエディタ側に通知する
 * - 何も無いところをドラッグすると移動 (パン)
 * - トラックパッドのピンチ、または Ctrl/Cmd + ホイールで拡大縮小
 */
class BpmnDiagramCanvas :
    JComponent(),
    Scrollable {

    companion object {
        /** 「ウィンドウに合わせる」ときに図の外側へ残す余白。 */
        private const val FIT_MARGIN = 24.0

        /** PNG 書き出しの余白。 */
        private const val EXPORT_MARGIN = 24.0

        const val MIN_ZOOM = 0.2
        const val MAX_ZOOM = 4.0
        private const val ZOOM_STEP = 1.15

        /** 「ウィンドウに合わせる」で拡大する上限。 */
        private const val MAX_FIT_ZOOM = 1.5

        /** 接続線のクリック判定に使う許容距離 (モデル座標)。 */
        private const val EDGE_HIT_TOLERANCE = 6.0

        /** ホイール 1 目盛りで動かす量 (px)。 */
        private const val SCROLL_UNIT = 16

        /**
         * ビューポートが無い場面 (テストなど) で使う、図のまわりの余白。
         * 通常はビューポート 1 枚分を使う。
         */
        private const val MIN_PADDING = 200.0

        /** 格子の間隔 (モデル座標)。BPMN の図形寸法に合わせた値。 */
        private const val GRID_MODEL_STEP = 20.0

        /** 画面上での格子の間隔をこの範囲に収める。詰まりすぎ / 空きすぎを防ぐ。 */
        private const val MIN_GRID_PX = 14.0
        private const val MAX_GRID_PX = 56.0
    }

    var onElementSelected: ((Any?) -> Unit)? = null
    var onZoomChanged: (() -> Unit)? = null

    private var diagram: BpmnDiagram = BpmnDiagram.EMPTY
    private var extent: Rectangle2D? = null

    var zoom: Double = 1.0
        private set

    private var selection: Any? = null
    private var panOrigin: Point? = null
    private var panViewPosition: Point? = null

    /** 設定されていれば編集できる。null なら読み取り専用。 */
    var editListener: BpmnCanvasEditListener? = null

    /** パレットで選んでいる要素。次にキャンバスを押した位置に置かれる。 */
    var armedPaletteItem: BpmnPaletteItem? = null
        set(value) {
            field = value
            cursor =
                if (value == null) Cursor.getDefaultCursor() else Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
        }

    /** いま行っている操作。 */
    private var gesture: Gesture = Gesture.None

    private sealed interface Gesture {
        data object None : Gesture

        /** 図形の移動。[origin] は押した位置 (モデル座標)。 */
        data class Move(val node: BpmnNode, val origin: BpmnPoint, val start: BpmnBounds) : Gesture

        /** 角のつまみによる大きさ変更。 */
        data class Resize(val node: BpmnNode, val handle: BpmnHandle, val start: BpmnBounds) : Gesture

        /** 線を引いている最中。[to] は現在のカーソル位置 (モデル座標)。 */
        data class Connect(val source: BpmnNode, val to: BpmnPoint, val target: BpmnNode?) : Gesture
    }

    /** 移動・大きさ変更中に見せる仮の矩形。確定するまで XML には書かない。 */
    private var previewBounds: BpmnBounds? = null

    /**
     * 「ウィンドウに合わせて中央へ」をレイアウト確定後にやり直す必要があるか。
     * エディタを開いた直後はビューポートのサイズがまだ 0 で、
     * そのまま計算すると極端に縮小されてしまう。
     */
    private var pendingFit = false

    init {
        isOpaque = true
        background = BpmnColors.CANVAS
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                if (!SwingUtilities.isLeftMouseButton(e)) return
                if (placeArmedItem(e.point)) return
                if (beginEditGesture(e)) return

                val element = elementAt(e.point)
                if (element == null) {
                    panOrigin = e.locationOnScreen
                    panViewPosition = viewport()?.viewPosition?.let { Point(it) }
                    select(null, scroll = false)
                } else {
                    selection = element
                    repaint()
                    onElementSelected?.invoke(element)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                finishEditGesture()
                panOrigin = null
                panViewPosition = null
            }

            override fun mouseDragged(e: MouseEvent) {
                if (updateEditGesture(e)) return
                val origin = panOrigin ?: return
                val start = panViewPosition ?: return
                val viewport = viewport() ?: return
                val dx = e.locationOnScreen.x - origin.x
                val dy = e.locationOnScreen.y - origin.y
                val maxX = (width - viewport.width).coerceAtLeast(0)
                val maxY = (height - viewport.height).coerceAtLeast(0)
                viewport.viewPosition = Point(
                    (start.x - dx).coerceIn(0, maxX),
                    (start.y - dy).coerceIn(0, maxY),
                )
            }

            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) renameSelection()
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addMouseWheelListener { e -> handleWheel(e) }
        installMagnificator()
        installDeleteKey()
        isFocusable = true
        toolTipText = ""
    }

    // --- モデル --------------------------------------------------------------

    fun setDiagram(newDiagram: BpmnDiagram, fit: Boolean) {
        diagram = newDiagram
        extent = newDiagram.extent()
        // 再パース後も同じ要素を選び続けられるよう、id で選択を引き継ぐ
        selection = when (val current = selection) {
            is BpmnNode -> newDiagram.nodes.firstOrNull { it.id == current.id && it.id.isNotEmpty() }
            is BpmnEdge -> newDiagram.edges.firstOrNull { it.id == current.id && it.id.isNotEmpty() }
            else -> null
        }
        revalidate()
        if (fit) fitToWindow() else repaint()
    }

    fun getDiagram(): BpmnDiagram = diagram

    fun select(element: Any?, scroll: Boolean) {
        selection = element
        repaint()
        if (scroll) scrollToSelection()
    }

    fun selectedElement(): Any? = selection

    // --- 編集操作 ------------------------------------------------------------

    /** 編集できる状態か。 */
    private fun canEdit(): Boolean = editListener != null

    /** パレットで選んだ要素を置く。置いたら true。 */
    private fun placeArmedItem(point: Point): Boolean {
        val item = armedPaletteItem ?: return false
        val listener = editListener ?: return false

        val (x, y) = viewToModel(point)
        val size = BpmnAutoLayout.defaultSize(item.kind)
        // 押した位置が図形の中心に来るようにする
        val bounds = BpmnBounds(x - size.first / 2, y - size.second / 2, size.first, size.second)
        // 落とした先がサブプロセスなら、その中に入れる
        val container = diagram.nodeAt(x, y)?.takeIf { it.kind.isSubProcess }?.id

        armedPaletteItem = null
        listener.onCreate(item, bounds, container)
        return true
    }

    /** 押した位置から編集操作を始められるなら始める。 */
    private fun beginEditGesture(e: MouseEvent): Boolean {
        if (!canEdit()) return false
        val (x, y) = viewToModel(e.point)

        // 選択中の図形のつまみが最優先。図形本体より手前にある。
        val selected = selection as? BpmnNode
        val selectedBounds = selected?.bounds
        if (selected != null && selectedBounds != null) {
            handleAt(selectedBounds, e.point)?.let { handle ->
                gesture = if (handle == BpmnHandle.CONNECT) {
                    Gesture.Connect(selected, BpmnPoint(x, y), null)
                } else {
                    previewBounds = selectedBounds
                    Gesture.Resize(selected, handle, selectedBounds)
                }
                return true
            }
        }

        val node = diagram.nodeAt(x, y) ?: return false
        val bounds = node.bounds ?: return false
        if (node.id.isEmpty()) return false

        // Shift + ドラッグでも線を引ける。つまみを探さずに済む近道。
        if (e.isShiftDown) {
            selection = node
            gesture = Gesture.Connect(node, BpmnPoint(x, y), null)
            repaint()
            return true
        }

        selection = node
        onElementSelected?.invoke(node)
        previewBounds = bounds
        gesture = Gesture.Move(node, BpmnPoint(x, y), bounds)
        repaint()
        return true
    }

    /** ドラッグ中の見た目を更新する。操作中なら true。 */
    private fun updateEditGesture(e: MouseEvent): Boolean {
        val (x, y) = viewToModel(e.point)
        when (val current = gesture) {
            is Gesture.Move -> {
                previewBounds = current.start.translate(x - current.origin.x, y - current.origin.y)
                repaint()
                return true
            }

            is Gesture.Resize -> {
                previewBounds = BpmnHandles.resize(current.start, current.handle, x, y)
                repaint()
                return true
            }

            is Gesture.Connect -> {
                val target = diagram.nodeAt(x, y)?.takeIf { it.id.isNotEmpty() && it.id != current.source.id }
                gesture = current.copy(to = BpmnPoint(x, y), target = target)
                repaint()
                return true
            }

            Gesture.None -> return false
        }
    }

    /** 指を離したところで確定し、XML へ書き戻す。 */
    private fun finishEditGesture() {
        val listener = editListener
        when (val current = gesture) {
            is Gesture.Move -> {
                val bounds = previewBounds
                if (listener != null && bounds != null && bounds != current.start) {
                    listener.onBoundsChanged(current.node.id, bounds, isResize = false)
                }
            }

            is Gesture.Resize -> {
                val bounds = previewBounds
                if (listener != null && bounds != null && bounds != current.start) {
                    listener.onBoundsChanged(current.node.id, bounds, isResize = true)
                }
            }

            is Gesture.Connect -> {
                val target = current.target
                if (listener != null && target != null) {
                    listener.onConnect(current.source.id, target.id)
                }
            }

            Gesture.None -> Unit
        }
        gesture = Gesture.None
        previewBounds = null
        repaint()
    }

    /** [point] にあるつまみ。無ければ null。 */
    private fun handleAt(bounds: BpmnBounds, point: Point): BpmnHandle? =
        BpmnHandle.entries.firstOrNull { handle ->
            val (hx, hy) = BpmnHandles.center(bounds, handle)
            val view = modelToView(hx, hy)
            val radius = BpmnHandles.RADIUS + 2
            kotlin.math.abs(view.x - point.x) <= radius && kotlin.math.abs(view.y - point.y) <= radius
        }

    /** Delete / Backspace で選択中の要素を消す。 */
    private fun installDeleteKey() {
        val action = object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val listener = editListener ?: return
                val node = selection as? BpmnNode ?: return
                if (node.id.isEmpty()) return
                listener.onDelete(listOf(node.id))
                selection = null
            }
        }
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "bpmn.delete")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "bpmn.delete")
        actionMap.put("bpmn.delete", action)
    }

    /** ダブルクリックで名前を編集する。 */
    private fun renameSelection() {
        val listener = editListener ?: return
        val node = selection as? BpmnNode ?: return
        if (node.id.isEmpty()) return

        val entered = JOptionPane.showInputDialog(
            this,
            FlowableBundle.message("edit.rename.prompt"),
            node.name.orEmpty(),
        ) ?: return
        if (entered != node.name.orEmpty()) listener.onRename(node.id, entered)
    }

    // --- ズーム --------------------------------------------------------------

    fun zoomIn() = setZoom(zoom * ZOOM_STEP, viewportCenter())

    fun zoomOut() = setZoom(zoom / ZOOM_STEP, viewportCenter())

    fun resetZoom() = setZoom(1.0, viewportCenter())

    /** 図全体が収まる倍率にして、中央に置く。 */
    fun fitToWindow() {
        val bounds = extent ?: return
        if (bounds.width <= 0 || bounds.height <= 0) return

        val viewport = viewport()
        if (viewport == null || viewport.width <= FIT_MARGIN * 2 || viewport.height <= FIT_MARGIN * 2) {
            // まだ配置されていないので、最初に描画されるときにやり直す
            pendingFit = true
            return
        }

        pendingFit = false
        val scaleX = (viewport.width - FIT_MARGIN * 2) / bounds.width
        val scaleY = (viewport.height - FIT_MARGIN * 2) / bounds.height
        // 小さな図を拡大しすぎても読みやすくならないので上限を設ける
        setZoom(minOf(scaleX, scaleY, MAX_FIT_ZOOM), null)
        centerContent()
    }

    /** 図をビューポートの中央に置く。 */
    fun centerContent() {
        val bounds = extent ?: return
        val viewport = viewport() ?: return
        viewport.doLayout()

        val size = preferredSize
        val maxX = (size.width - viewport.width).coerceAtLeast(0)
        val maxY = (size.height - viewport.height).coerceAtLeast(0)
        viewport.viewPosition = Point(
            (paddingX() + bounds.width * zoom / 2 - viewport.width / 2.0).roundToInt().coerceIn(0, maxX),
            (paddingY() + bounds.height * zoom / 2 - viewport.height / 2.0).roundToInt().coerceIn(0, maxY),
        )
        repaint()
    }

    private fun setZoom(value: Double, anchor: Point?) {
        val clamped = value.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (clamped == zoom) return

        val viewport = viewport()
        val anchorModel = anchor?.let { viewToModel(it) }

        zoom = clamped
        revalidate()
        // スクロール位置を合わせる前に、新しい大きさをレイアウトへ反映させる。
        // revalidate() は次のレイアウトまで効かないので、ここで一度走らせる。
        viewport?.doLayout()

        if (viewport != null && anchor != null && anchorModel != null) {
            // 拡大の基点にした図形が動かないようスクロール位置を補正する
            val newViewPoint = modelToView(anchorModel.first, anchorModel.second)
            val current = viewport.viewPosition
            val offsetX = anchor.x - current.x
            val offsetY = anchor.y - current.y
            viewport.viewPosition = Point(
                (newViewPoint.x - offsetX).coerceAtLeast(0),
                (newViewPoint.y - offsetY).coerceAtLeast(0),
            )
        }
        repaint()
        onZoomChanged?.invoke()
    }

    /**
     * macOS のトラックパッドのピンチ操作に対応する。
     *
     * JBScrollPane が使う JBViewport がこのクライアントプロパティを見ており、
     * ジェスチャ中は拡大した見た目を出したうえで、指を離した時点でここが呼ばれる。
     * 受け取るのも返すのもビュー座標で、返した点が元の位置に来るよう
     * ビューポートが自分でスクロールを合わせてくれる。
     */
    private fun installMagnificator() {
        ClientProperty.put(
            this,
            Magnificator.CLIENT_PROPERTY_KEY,
            Magnificator { scale, at ->
                val (modelX, modelY) = viewToModel(at)
                // スクロール位置の調整はビューポート側の担当なので anchor は渡さない
                setZoom(zoom * scale, null)
                modelToView(modelX, modelY)
            },
        )
    }

    private fun handleWheel(e: MouseWheelEvent) {
        if (!isZoomGesture(e)) {
            // 2 本指スクロールなどはそのままスクロールとして扱う
            forwardToScrollPane(e)
            return
        }

        // preciseWheelRotation はトラックパッドだと 1 未満の細かい値になる。
        // 指数に使うことで、マウスのホイール 1 段は従来どおり、
        // トラックパッドはなめらかに拡大縮小できる。
        val rotation = e.preciseWheelRotation
        if (rotation == 0.0) return
        setZoom(zoom * ZOOM_STEP.pow(-rotation), e.point)
        e.consume()
    }

    /** 修飾キー付きのホイール操作をズームとみなす (macOS は Cmd、その他は Ctrl)。 */
    private fun isZoomGesture(e: MouseWheelEvent): Boolean = e.isControlDown || e.isMetaDown

    private fun forwardToScrollPane(e: MouseWheelEvent) {
        // AWT はホイールイベントを「リスナを持つ最も内側のコンポーネント」に配送し、
        // そこにリスナがある限り親へは流さない (Component.dispatchEventImpl の MOUSE_WHEEL 分岐)。
        // 拡大縮小のためにリスナを付けている以上、スクロール担当までは自分で送り直す。
        val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, this) ?: parent ?: return
        scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, scrollPane))
    }

    // --- 座標変換 ------------------------------------------------------------

    private fun originX(): Double = extent?.x ?: 0.0

    private fun originY(): Double = extent?.y ?: 0.0

    /**
     * 図の左右に取る余白。ビューポート 1 枚分あるので、
     * 図を右端まで送っても左端まで戻しても行き止まりに当たらない。
     */
    private fun paddingX(): Double = (viewport()?.width?.toDouble() ?: 0.0).coerceAtLeast(MIN_PADDING)

    private fun paddingY(): Double = (viewport()?.height?.toDouble() ?: 0.0).coerceAtLeast(MIN_PADDING)

    private fun modelToView(x: Double, y: Double): Point =
        Point(
            ((x - originX()) * zoom + paddingX()).roundToInt(),
            ((y - originY()) * zoom + paddingY()).roundToInt(),
        )

    private fun viewToModel(point: Point): Pair<Double, Double> =
        (point.x - paddingX()) / zoom + originX() to (point.y - paddingY()) / zoom + originY()

    /** 図の左上がビュー座標のどこに来るか。倍率を変えても余白は変わらない。 */
    @TestOnly
    internal fun contentOriginInView(): Point = Point(paddingX().roundToInt(), paddingY().roundToInt())

    private fun elementAt(point: Point): Any? {
        val (x, y) = viewToModel(point)
        return diagram.nodeAt(x, y) ?: diagram.edgeAt(x, y, EDGE_HIT_TOLERANCE / zoom)
    }

    private fun viewport(): JViewport? = parent as? JViewport

    private fun viewportCenter(): Point? {
        val viewport = viewport() ?: return null
        val position = viewport.viewPosition
        return Point(position.x + viewport.width / 2, position.y + viewport.height / 2)
    }

    private fun scrollToSelection() {
        val bounds = when (val element = selection) {
            is BpmnNode -> element.bounds?.toRectangle2D()

            is BpmnEdge -> element.waypoints.takeIf { it.size >= 2 }?.let { points ->
                var rect: Rectangle2D = Rectangle2D.Double(points[0].x, points[0].y, 0.0, 0.0)
                points.forEach { rect = rect.createUnion(Rectangle2D.Double(it.x, it.y, 0.0, 0.0)) }
                rect
            }

            else -> null
        } ?: return

        val topLeft = modelToView(bounds.x, bounds.y)
        val size = Dimension((bounds.width * zoom).roundToInt(), (bounds.height * zoom).roundToInt())
        val padding = 40
        scrollRectToVisible(
            Rectangle(
                topLeft.x - padding, topLeft.y - padding,
                size.width + padding * 2, size.height + padding * 2,
            ),
        )
    }

    // --- スクロール ----------------------------------------------------------

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        SCROLL_UNIT

    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width

    // 余白を持たせている以上つねにビューポートより大きいので、追従はさせない
    override fun getScrollableTracksViewportWidth(): Boolean = false

    override fun getScrollableTracksViewportHeight(): Boolean = false

    // --- 描画 ----------------------------------------------------------------

    override fun getPreferredSize(): Dimension {
        val bounds = extent ?: return JBUI.size(400, 300)
        return Dimension(
            (bounds.width * zoom + paddingX() * 2).roundToInt().coerceAtLeast(1),
            (bounds.height * zoom + paddingY() * 2).roundToInt().coerceAtLeast(1),
        )
    }

    override fun paintComponent(g: Graphics) {
        if (pendingFit) fitToWindow()

        val g2 = g.create() as Graphics2D
        try {
            g2.color = BpmnColors.CANVAS
            g2.fillRect(0, 0, width, height)
            paintGrid(g2)

            if (diagram.isEmpty) {
                paintPlaceholder(g2)
                return
            }

            g2.translate(paddingX(), paddingY())
            g2.scale(zoom, zoom)
            g2.translate(-originX(), -originY())
            painter().paint(g2, diagram, selection)
            paintEditOverlay(g2)
        } finally {
            g2.dispose()
        }
    }

    /**
     * 編集中の見た目を、図の上に重ねて描く。
     *
     * ドラッグ中の矩形や引きかけの線はまだ XML に書いていない。
     * 指を離すまでは、ここで見せているだけ。
     */
    private fun paintEditOverlay(g: Graphics2D) {
        if (!canEdit()) return
        val stroke = BasicStroke(
            (1.5 / zoom).toFloat(),
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND,
            10f,
            floatArrayOf((4 / zoom).toFloat(), (3 / zoom).toFloat()),
            0f,
        )

        // 移動 / 大きさ変更の下書き
        previewBounds?.let { bounds ->
            paintReroutedEdges(g, bounds, stroke)
            g.color = BpmnColors.SELECTION
            g.stroke = stroke
            g.draw(bounds.toRectangle2D())
        }

        when (val current = gesture) {
            is Gesture.Connect -> paintConnectionPreview(g, current, stroke)
            else -> Unit
        }

        // つまみは選択中の図形にだけ出す
        val bounds = previewBounds ?: (selection as? BpmnNode)?.bounds ?: return
        if (gesture is Gesture.Connect) return
        paintHandles(g, bounds)
    }

    /**
     * ドラッグ中の図形に繋がる線を、動かした先に合わせて引き直して見せる。
     *
     * 実際の書き換えは指を離してからなので、ここで見せているのは下書き。
     * これが無いと、図形だけが動いて線が取り残されたように見えてしまう。
     */
    private fun paintReroutedEdges(g: Graphics2D, bounds: BpmnBounds, stroke: BasicStroke) {
        val movedId = when (val current = gesture) {
            is Gesture.Move -> current.node.id
            is Gesture.Resize -> current.node.id
            else -> null
        } ?: return
        if (movedId.isEmpty()) return

        g.color = BpmnColors.SELECTION
        g.stroke = stroke
        for (edge in diagram.edges) {
            if (edge.sourceRef != movedId && edge.targetRef != movedId) continue
            val source = if (edge.sourceRef == movedId) bounds else diagram.nodesById[edge.sourceRef]?.bounds
            val target = if (edge.targetRef == movedId) bounds else diagram.nodesById[edge.targetRef]?.bounds
            val points = BpmnGeometry.reroute(edge.waypoints, source, target)
            if (points.size < 2) continue
            g.draw(
                Path2D.Double().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                },
            )
        }
    }

    private fun paintConnectionPreview(g: Graphics2D, gesture: Gesture.Connect, stroke: BasicStroke) {
        val from = gesture.source.bounds ?: return
        g.color = BpmnColors.SELECTION
        g.stroke = stroke
        g.draw(Line2D.Double(from.centerX, from.centerY, gesture.to.x, gesture.to.y))

        // 繋がる先を縁取って、どこに落ちるかを示す
        gesture.target?.bounds?.let { target ->
            val margin = 4.0
            g.draw(
                Rectangle2D.Double(
                    target.x - margin,
                    target.y - margin,
                    target.width + margin * 2,
                    target.height + margin * 2,
                ),
            )
        }
    }

    private fun paintHandles(g: Graphics2D, bounds: BpmnBounds) {
        val radius = BpmnHandles.RADIUS / zoom
        g.stroke = BasicStroke((1.0 / zoom).toFloat())
        for (handle in BpmnHandle.entries) {
            val (x, y) = BpmnHandles.center(bounds, handle)
            val shape = Rectangle2D.Double(x - radius, y - radius, radius * 2, radius * 2)
            if (handle == BpmnHandle.CONNECT) {
                // 線を引くつまみは丸にして、大きさ変更のつまみと見分ける
                val circle = Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2)
                g.color = BpmnColors.SELECTION
                g.fill(circle)
                g.color = BpmnColors.CANVAS
                g.draw(circle)
            } else {
                g.color = BpmnColors.CANVAS
                g.fill(shape)
                g.color = BpmnColors.SELECTION
                g.draw(shape)
            }
        }
    }

    /**
     * 点の格子を敷く。
     *
     * 図を画面外まで動かせるようにした分、何も無いところでは動いたかどうかが
     * 分かりにくい。格子があると移動量と拡大率が目で追える。
     * 描くのは可視範囲だけなので、広いキャンバスでも負荷は増えない。
     */
    private fun paintGrid(g: Graphics2D) {
        val clip = g.clipBounds ?: Rectangle(0, 0, width, height)
        if (clip.width <= 0 || clip.height <= 0) return

        val step = gridStepPx()
        g.color = BpmnColors.GRID

        var x = firstGridLine(paddingX(), step, clip.x.toDouble())
        while (x <= clip.x + clip.width) {
            var y = firstGridLine(paddingY(), step, clip.y.toDouble())
            while (y <= clip.y + clip.height) {
                g.fillRect(x.roundToInt(), y.roundToInt(), 1, 1)
                y += step
            }
            x += step
        }
    }

    /** 画面上の間隔が読みやすい範囲に収まるよう、2 の冪で調整した格子の間隔 (px)。 */
    private fun gridStepPx(): Double {
        var step = GRID_MODEL_STEP * zoom
        while (step < MIN_GRID_PX) step *= 2
        while (step > MAX_GRID_PX) step /= 2
        return step
    }

    /** [from] 以上で最初に来る格子線の位置。格子は図の原点に合わせる。 */
    private fun firstGridLine(anchor: Double, step: Double, from: Double): Double =
        anchor + ceil((from - anchor) / step) * step

    private fun paintPlaceholder(g: Graphics2D) {
        g.color = BpmnColors.MUTED_TEXT
        g.font = UIUtil.getLabelFont()
        val text = FlowableBundle.message("preview.empty")
        val metrics = g.fontMetrics
        g.drawString(text, (width - metrics.stringWidth(text)) / 2, height / 2)
    }

    private fun painter() = BpmnDiagramPainter(UIUtil.getLabelFont())

    override fun getToolTipText(event: MouseEvent): String? = when (val element = elementAt(event.point)) {
        is BpmnNode -> buildString {
            append("<html><b>").append(escape(element.displayLabel)).append("</b><br/>")
            append(element.kind.displayName)
            if (element.id.isNotEmpty()) append("<br/><code>").append(escape(element.id)).append("</code>")
            element.eventDefinition?.let { append("<br/>").append(escape(it)).append(" event") }
            append("</html>")
        }

        is BpmnEdge -> buildString {
            append("<html><b>").append(escape(element.displayLabel)).append("</b><br/>")
            append(escape(element.sourceRef.orEmpty())).append(" → ").append(escape(element.targetRef.orEmpty()))
            if (element.hasCondition) append("<br/>conditional")
            if (element.isDefaultFlow) append("<br/>default flow")
            append("</html>")
        }

        else -> null
    }

    private fun escape(text: String): String = com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(text)

    /**
     * 現在の図を PNG 用の画像に描き出す。
     * 書き出しでは格子を描かない。図だけが欲しい場面で背景の点は邪魔になる。
     */
    fun renderToImage(scale: Double): BufferedImage? {
        val bounds = extent ?: return null
        val width = (bounds.width * scale + EXPORT_MARGIN * 2).roundToInt().coerceAtLeast(1)
        val height = (bounds.height * scale + EXPORT_MARGIN * 2).roundToInt().coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.color = BpmnColors.CANVAS
            g.fillRect(0, 0, width, height)
            g.translate(EXPORT_MARGIN, EXPORT_MARGIN)
            g.scale(scale, scale)
            g.translate(-bounds.x, -bounds.y)
            painter().paint(g, diagram, null)
        } finally {
            g.dispose()
        }
        return image
    }
}
