package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.editor.BpmnColors
import com.github.gekal.flowableeditor.editor.BpmnDiagramCanvas
import com.github.gekal.flowableeditor.model.BpmnBounds
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.github.gekal.flowableeditor.model.BpmnNode
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.ClientProperty
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.Magnificator
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

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
        // 図の左上から右に 100、下に 50 のところ (倍率 1 なのでモデル座標と同じ)
        val origin = canvas.contentOriginInView()
        val anchor = Point(origin.x + 100, origin.y + 50)

        val moved = magnificator().magnify(2.0, anchor)

        // 倍率 2 では同じ場所が原点から 2 倍離れた位置に来る。
        // ビューポートはこの差分だけスクロールして指の位置を保つ。
        assertEquals(origin.x + 200, moved.x)
        assertEquals(origin.y + 100, moved.y)
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

    // --- 表示位置 ------------------------------------------------------------

    fun `test the diagram is centred in the viewport`() {
        canvas.centerContent()

        val viewport = scrollPane.viewport
        val origin = canvas.contentOriginInView()
        val viewportCentreX = viewport.viewPosition.x + viewport.width / 2
        val viewportCentreY = viewport.viewPosition.y + viewport.height / 2

        // 図は (0,0)-(500,380)。倍率 1 なので中心は左上から (250, 190)。
        assertEquals("横方向の中央", (origin.x + 250).toDouble(), viewportCentreX.toDouble(), 1.0)
        assertEquals("縦方向の中央", (origin.y + 190).toDouble(), viewportCentreY.toDouble(), 1.0)
    }

    fun `test fitting to the window also centres the diagram`() {
        scrollPane.viewport.viewPosition = Point(0, 0)

        canvas.fitToWindow()

        val viewport = scrollPane.viewport
        val origin = canvas.contentOriginInView()
        val viewportCentreX = viewport.viewPosition.x + viewport.width / 2
        val expected = origin.x + (500 * canvas.zoom / 2)
        assertEquals("倍率を変えても中央に置く", expected, viewportCentreX.toDouble(), 1.5)
    }

    fun `test the diagram can be pushed out of view in every direction`() {
        // 図のまわりにビューポート 1 枚分の余白があるので、
        // どちらの端まで送っても図を画面の外に出しきれる。
        val viewport = scrollPane.viewport
        val origin = canvas.contentOriginInView()
        val size = canvas.preferredSize
        val contentWidth = (500 * canvas.zoom).roundToInt()
        val contentHeight = (380 * canvas.zoom).roundToInt()

        assertTrue("左に送り切れる", origin.x >= viewport.width)
        assertTrue("上に送り切れる", origin.y >= viewport.height)
        assertTrue("右に送り切れる", size.width - viewport.width >= origin.x + contentWidth)
        assertTrue("下に送り切れる", size.height - viewport.height >= origin.y + contentHeight)
    }

    // --- 格子 ----------------------------------------------------------------

    /** キャンバスを画像に描いて中身を調べる。 */
    private fun renderCanvas(width: Int = 320, height: Int = 240, name: String = "canvas"): BufferedImage {
        canvas.size = Dimension(width, height)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            canvas.paint(g)
        } finally {
            g.dispose()
        }
        val output = File("build/reports/bpmn-render/$name.png")
        output.parentFile.mkdirs()
        ImageIO.write(image, "PNG", output)
        return image
    }

    /**
     * 格子の点だけを数える。
     *
     * 「背景以外」で数えると図形やプレースホルダの文字まで拾ってしまい、
     * 格子を消しても気付けない。格子の色そのものと突き合わせる。
     */
    private fun countGridDots(image: BufferedImage): Int {
        val grid = BpmnColors.GRID.rgb
        var count = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) == grid) count++
            }
        }
        return count
    }

    fun `test the grid sits behind the diagram`() {
        canvas.centerContent()

        val dots = countGridDots(renderCanvas(width = 520, height = 400, name = "canvas-with-diagram"))

        assertTrue("図の背後に格子がある (見つかった点: $dots)", dots > 100)
    }

    fun `test the grid is drawn on the empty area too`() {
        canvas.setDiagram(BpmnDiagram.EMPTY, fit = false)

        val dots = countGridDots(renderCanvas())

        assertTrue("図が無くても格子は出る (見つかった点: $dots)", dots > 20)
    }

    fun `test the grid keeps a readable spacing at every zoom level`() {
        // 拡大率をどう振っても、点が詰まりすぎたり消えたりしない
        for (zoom in listOf(BpmnDiagramCanvas.MIN_ZOOM, 0.5, 1.0, 2.0, BpmnDiagramCanvas.MAX_ZOOM)) {
            canvas.setDiagram(BpmnDiagram.EMPTY, fit = false)
            canvas.resetZoom()
            magnificator().magnify(zoom, Point(0, 0))

            val dots = countGridDots(renderCanvas())
            val area = 320 * 240
            assertTrue("倍率 $zoom で格子が消えている", dots > 20)
            assertTrue("倍率 $zoom で格子が詰まりすぎている ($dots / $area)", dots < area / 20)
        }
    }
}
