package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.editor.BpmnDiagramCanvas
import com.github.gekal.flowableeditor.model.BpmnBounds
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.github.gekal.flowableeditor.model.BpmnNode
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.ClientProperty
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.Magnificator
import java.awt.Point
import java.awt.event.MouseWheelEvent

/**
 * トラックパッド / ホイールの操作。
 *
 * ジェスチャは手で試すしかない部分が多いので、せめて
 * 「ピンチ = 拡大縮小」「2 本指スクロール = スクロール」の切り分けと、
 * 指の位置が動かないことは自動で見張っておく。
 */
class BpmnDiagramCanvasTest : BasePlatformTestCase() {

    private lateinit var canvas: BpmnDiagramCanvas
    private lateinit var scrollPane: JBScrollPane

    /** 原点 (0,0) 始まりの単純な図。座標計算を目で追えるようにしている。 */
    private fun sampleDiagram() = BpmnDiagram(
        nodes = listOf(
            BpmnNode(
                id = "first",
                name = "First",
                kind = BpmnElementKind.USER_TASK,
                tagName = "userTask",
                bounds = BpmnBounds(0.0, 0.0, 100.0, 80.0),
            ),
            BpmnNode(
                id = "second",
                name = "Second",
                kind = BpmnElementKind.USER_TASK,
                tagName = "userTask",
                bounds = BpmnBounds(400.0, 300.0, 100.0, 80.0),
            ),
        ),
        edges = emptyList(),
        hasDiagramInterchange = true,
    )

    override fun setUp() {
        super.setUp()
        canvas = BpmnDiagramCanvas()
        scrollPane = JBScrollPane(canvas)
        scrollPane.setSize(200, 150)
        canvas.setDiagram(sampleDiagram(), fit = false)
        layOut()
    }

    private fun layOut() {
        scrollPane.doLayout()
        scrollPane.viewport.doLayout()
        canvas.size = canvas.preferredSize
    }

    private fun magnificator(): Magnificator =
        requireNotNull(ClientProperty.get(canvas, Magnificator.CLIENT_PROPERTY_KEY)) {
            "ピンチ操作用の Magnificator が登録されていない"
        }

    private fun wheelEvent(rotation: Double, modifiers: Int, at: Point = Point(60, 50)) =
        MouseWheelEvent(
            canvas,
            MouseWheelEvent.MOUSE_WHEEL,
            System.currentTimeMillis(),
            modifiers,
            at.x,
            at.y,
            // 画面上の絶対座標。この検査では使われないので同じ値でよい。
            at.x,
            at.y,
            0,
            false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL,
            1,
            if (rotation > 0) 1 else -1,
            rotation,
        )

    // --- ピンチ --------------------------------------------------------------

    fun `test the canvas offers a magnificator for trackpad pinch`() {
        assertNotNull(ClientProperty.get(canvas, Magnificator.CLIENT_PROPERTY_KEY))
    }

    fun `test pinching out zooms in`() {
        assertEquals(1.0, canvas.zoom, 0.001)

        magnificator().magnify(2.0, Point(24, 24))

        assertEquals(2.0, canvas.zoom, 0.001)
    }

    fun `test pinching in zooms out`() {
        magnificator().magnify(0.5, Point(24, 24))

        assertEquals(0.5, canvas.zoom, 0.001)
    }

    fun `test pinch keeps the point under the fingers in place`() {
        // 余白 24px なので、ビュー座標 (124, 74) は倍率 1 でモデル座標 (100, 50)
        val anchor = Point(124, 74)

        val moved = magnificator().magnify(2.0, anchor)

        // 倍率 2 では同じモデル座標が (224, 124) に来る。
        // ビューポートはこの差分だけスクロールして指の位置を保つ。
        assertEquals(224, moved.x)
        assertEquals(124, moved.y)
    }

    fun `test pinch respects the zoom limits`() {
        magnificator().magnify(100.0, Point(24, 24))
        assertEquals(BpmnDiagramCanvas.MAX_ZOOM, canvas.zoom, 0.001)

        magnificator().magnify(0.0001, Point(24, 24))
        assertEquals(BpmnDiagramCanvas.MIN_ZOOM, canvas.zoom, 0.001)
    }

    // --- ホイール ------------------------------------------------------------

    fun `test command wheel zooms`() {
        val before = canvas.zoom

        canvas.dispatchEvent(wheelEvent(-1.0, MouseWheelEvent.META_DOWN_MASK))
        assertTrue("Cmd + ホイールで拡大する", canvas.zoom > before)

        canvas.dispatchEvent(wheelEvent(1.0, MouseWheelEvent.META_DOWN_MASK))
        assertEquals(before, canvas.zoom, 0.001)
    }

    fun `test control wheel zooms too`() {
        val before = canvas.zoom

        canvas.dispatchEvent(wheelEvent(-1.0, MouseWheelEvent.CTRL_DOWN_MASK))

        assertTrue(canvas.zoom > before)
    }

    fun `test a trackpad pinch through the wheel zooms smoothly`() {
        // トラックパッドは 1 未満の細かい回転量を送ってくる。
        // 1 段ぶんの拡大より小さく、かつ確かに拡大されること。
        val oneNotch = run {
            canvas.dispatchEvent(wheelEvent(-1.0, MouseWheelEvent.META_DOWN_MASK))
            canvas.zoom.also { canvas.resetZoom() }
        }

        canvas.dispatchEvent(wheelEvent(-0.1, MouseWheelEvent.META_DOWN_MASK))

        assertTrue("細かい回転でも拡大する", canvas.zoom > 1.0)
        assertTrue("1 段ぶんより小さい", canvas.zoom < oneNotch)
    }

    fun `test two finger scroll scrolls instead of zooming`() {
        val before = canvas.zoom

        canvas.dispatchEvent(wheelEvent(1.0, 0))

        assertEquals("修飾キー無しのスクロールで倍率は変わらない", before, canvas.zoom, 0.001)
    }

    fun `test two finger scroll is forwarded to the scroll pane exactly once`() {
        // ホイールリスナを付けた時点で AWT はこのコンポーネントを配送先に選び、
        // 親へは流さなくなる (Component.dispatchEventImpl の MOUSE_WHEEL 分岐)。
        // そのためスクロール担当までは自分で送り直す必要がある。
        val received = mutableListOf<MouseWheelEvent>()
        scrollPane.addMouseWheelListener { received += it }

        canvas.dispatchEvent(wheelEvent(1.0, 0))

        assertEquals("スクロールペインにちょうど 1 回届く", 1, received.size)
        assertEquals("回転量がそのまま伝わる", 1.0, received.single().preciseWheelRotation, 0.001)
    }

    fun `test a zooming wheel event is not forwarded as a scroll`() {
        val received = mutableListOf<MouseWheelEvent>()
        scrollPane.addMouseWheelListener { received += it }

        canvas.dispatchEvent(wheelEvent(-1.0, MouseWheelEvent.META_DOWN_MASK))

        assertTrue("拡大縮小したぶんはスクロールに回さない", received.isEmpty())
    }
}
