package com.github.gekal.flowableeditor.editor

import com.github.gekal.flowableeditor.FlowableBundle
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnEdge
import com.github.gekal.flowableeditor.model.BpmnNode
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JViewport
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

/**
 * BPMN 図を表示するキャンバス。
 *
 * - 図形クリックで選択し、[onElementSelected] でエディタ側に通知する
 * - 何も無いところをドラッグするとスクロール (パン)
 * - Ctrl/Cmd + ホイールでカーソル位置を中心に拡大縮小
 */
class BpmnDiagramCanvas : JComponent() {

    companion object {
        private const val MARGIN = 24.0
        const val MIN_ZOOM = 0.2
        const val MAX_ZOOM = 4.0
        private const val ZOOM_STEP = 1.15

        /** 「ウィンドウに合わせる」で拡大する上限。 */
        private const val MAX_FIT_ZOOM = 1.5

        /** 接続線のクリック判定に使う許容距離 (モデル座標)。 */
        private const val EDGE_HIT_TOLERANCE = 6.0
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

    /**
     * 「ウィンドウに合わせる」をレイアウト確定後にやり直す必要があるか。
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
                val element = elementAt(e.point)
                if (element == null && SwingUtilities.isLeftMouseButton(e)) {
                    panOrigin = e.locationOnScreen
                    panViewPosition = viewport()?.viewPosition?.let { Point(it) }
                }
                if (element != null) {
                    selection = element
                    repaint()
                    onElementSelected?.invoke(element)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                panOrigin = null
                panViewPosition = null
            }

            override fun mouseDragged(e: MouseEvent) {
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
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addMouseWheelListener { e -> handleWheel(e) }
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

    // --- ズーム --------------------------------------------------------------

    fun zoomIn() = setZoom(zoom * ZOOM_STEP, null)

    fun zoomOut() = setZoom(zoom / ZOOM_STEP, null)

    fun resetZoom() = setZoom(1.0, null)

    fun fitToWindow() {
        val bounds = extent ?: return
        if (bounds.width <= 0 || bounds.height <= 0) return

        val viewport = viewport()
        if (viewport == null || viewport.width <= MARGIN * 2 || viewport.height <= MARGIN * 2) {
            // まだ配置されていないので、最初に描画されるときにやり直す
            pendingFit = true
            return
        }

        pendingFit = false
        val scaleX = (viewport.width - MARGIN * 2) / bounds.width
        val scaleY = (viewport.height - MARGIN * 2) / bounds.height
        // 小さな図を拡大しすぎても読みやすくならないので上限を設ける
        setZoom(minOf(scaleX, scaleY, MAX_FIT_ZOOM), null)
        viewport.viewPosition = Point(0, 0)
    }

    private fun setZoom(value: Double, anchor: Point?) {
        val clamped = value.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (clamped == zoom) return

        val viewport = viewport()
        val anchorModel = anchor?.let { viewToModel(it) }

        zoom = clamped
        revalidate()
        // スクロール位置を合わせる前にサイズを反映させる (revalidate は非同期のため)
        size = preferredSize

        if (viewport != null && anchor != null && anchorModel != null) {
            // ホイール位置の図形が動かないようスクロール位置を補正する
            val newViewPoint = modelToView(anchorModel.first, anchorModel.second)
            val current = viewport.viewPosition
            val offsetInViewport = anchor.y - current.y
            val offsetXInViewport = anchor.x - current.x
            viewport.viewPosition = Point(
                (newViewPoint.x - offsetXInViewport).coerceAtLeast(0),
                (newViewPoint.y - offsetInViewport).coerceAtLeast(0),
            )
        }
        repaint()
        onZoomChanged?.invoke()
    }

    private fun handleWheel(e: MouseWheelEvent) {
        if (!e.isControlDown && !e.isMetaDown) {
            // 修飾キーが無いときは通常のスクロールとして親に流す
            val target = parent
            if (target != null) {
                target.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, target))
            }
            return
        }
        val factor = if (e.preciseWheelRotation < 0) ZOOM_STEP else 1 / ZOOM_STEP
        setZoom(zoom * factor, e.point)
    }

    // --- 座標変換 ------------------------------------------------------------

    private fun originX(): Double = extent?.x ?: 0.0

    private fun originY(): Double = extent?.y ?: 0.0

    private fun modelToView(x: Double, y: Double): Point =
        Point(
            ((x - originX()) * zoom + MARGIN).roundToInt(),
            ((y - originY()) * zoom + MARGIN).roundToInt(),
        )

    private fun viewToModel(point: Point): Pair<Double, Double> =
        (point.x - MARGIN) / zoom + originX() to (point.y - MARGIN) / zoom + originY()

    private fun elementAt(point: Point): Any? {
        val (x, y) = viewToModel(point)
        return diagram.nodeAt(x, y) ?: diagram.edgeAt(x, y, EDGE_HIT_TOLERANCE / zoom)
    }

    private fun viewport(): JViewport? = parent as? JViewport

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

    // --- 描画 ----------------------------------------------------------------

    override fun getPreferredSize(): Dimension {
        val bounds = extent ?: return JBUI.size(400, 300)
        return Dimension(
            (bounds.width * zoom + MARGIN * 2).roundToInt().coerceAtLeast(1),
            (bounds.height * zoom + MARGIN * 2).roundToInt().coerceAtLeast(1),
        )
    }

    override fun paintComponent(g: Graphics) {
        if (pendingFit) fitToWindow()

        val g2 = g.create() as Graphics2D
        try {
            g2.color = BpmnColors.CANVAS
            g2.fillRect(0, 0, width, height)

            if (diagram.isEmpty) {
                paintPlaceholder(g2)
                return
            }

            g2.translate(MARGIN, MARGIN)
            g2.scale(zoom, zoom)
            g2.translate(-originX(), -originY())
            painter().paint(g2, diagram, selection)
        } finally {
            g2.dispose()
        }
    }

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

    /** 現在の図を PNG 用の画像に描き出す。 */
    fun renderToImage(scale: Double): BufferedImage? {
        val bounds = extent ?: return null
        val width = ((bounds.width + MARGIN * 2 / scale) * scale).roundToInt().coerceAtLeast(1)
        val height = ((bounds.height + MARGIN * 2 / scale) * scale).roundToInt().coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.color = BpmnColors.CANVAS
            g.fillRect(0, 0, width, height)
            g.translate(MARGIN, MARGIN)
            g.scale(scale, scale)
            g.translate(-bounds.x, -bounds.y)
            painter().paint(g, diagram, null)
        } finally {
            g.dispose()
        }
        return image
    }
}
