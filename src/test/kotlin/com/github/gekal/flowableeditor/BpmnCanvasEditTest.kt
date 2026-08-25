package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.edit.BpmnPaletteItem
import com.github.gekal.flowableeditor.editor.BpmnCanvasEditListener
import com.github.gekal.flowableeditor.editor.BpmnColors
import com.github.gekal.flowableeditor.editor.BpmnDiagramCanvas
import com.github.gekal.flowableeditor.editor.BpmnHandle
import com.github.gekal.flowableeditor.editor.BpmnHandles
import com.github.gekal.flowableeditor.model.BpmnBounds
import com.github.gekal.flowableeditor.model.BpmnConnectionKind
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnEdge
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.github.gekal.flowableeditor.model.BpmnNode
import com.github.gekal.flowableeditor.model.BpmnPoint
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import java.awt.Dimension
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.KeyStroke

/**
 * 図の上での操作が、どういう要求として編集層に届くかを見る。
 *
 * 実際の書き戻しは [BpmnDocumentEditorTest] が受け持つので、ここでは
 * 「掴んだ場所」「離した場所」から正しい要求が組み立てられるかだけを確かめる。
 */
class BpmnCanvasEditTest : BasePlatformTestCase() {

    private lateinit var canvas: BpmnDiagramCanvas
    private lateinit var scrollPane: JBScrollPane
    private lateinit var recorded: Recorder

    private data class Created(
        val item: BpmnPaletteItem,
        val bounds: BpmnBounds,
        val containerId: String?,
        val attachToId: String?,
    )

    /** 届いた要求を記録するだけの受け手。 */
    private class Recorder : BpmnCanvasEditListener {
        val bounds = mutableListOf<Triple<String, BpmnBounds, Boolean>>()
        val connections = mutableListOf<Pair<String, String>>()
        val created = mutableListOf<Created>()
        val deleted = mutableListOf<List<String>>()
        val renamed = mutableListOf<Pair<String, String>>()
        val waypoints = mutableListOf<Pair<String, List<BpmnPoint>>>()

        override fun onBoundsChanged(elementId: String, bounds: BpmnBounds, isResize: Boolean) {
            this.bounds += Triple(elementId, bounds, isResize)
        }

        override fun onConnect(sourceId: String, targetId: String) {
            connections += sourceId to targetId
        }

        override fun onWaypointsChanged(edgeId: String, waypoints: List<BpmnPoint>) {
            this.waypoints += edgeId to waypoints
        }

        override fun onCreate(
            item: BpmnPaletteItem,
            bounds: BpmnBounds,
            containerId: String?,
            attachToId: String?,
        ) {
            created += Created(item, bounds, containerId, attachToId)
        }

        override fun onDelete(elementIds: List<String>) {
            deleted += elementIds
        }

        override fun onRename(elementId: String, name: String) {
            renamed += elementId to name
        }
    }

    private fun node(id: String, x: Double, y: Double, kind: BpmnElementKind = BpmnElementKind.USER_TASK) =
        BpmnNode(
            id = id,
            name = id,
            kind = kind,
            tagName = kind.tagName,
            bounds = BpmnBounds(x, y, 100.0, 80.0),
        )

    override fun setUp() {
        super.setUp()
        canvas = BpmnDiagramCanvas()
        scrollPane = JBScrollPane(canvas)
        scrollPane.setSize(400, 300)
        canvas.setDiagram(
            BpmnDiagram(
                nodes = listOf(node("first", 0.0, 0.0), node("second", 300.0, 200.0)),
                edges = emptyList(),
                hasDiagramInterchange = true,
            ),
            fit = false,
        )
        scrollPane.doLayout()
        scrollPane.viewport.doLayout()
        canvas.size = canvas.preferredSize

        recorded = Recorder()
        canvas.editListener = recorded
    }

    /** モデル座標をキャンバス上の座標に直す。 */
    private fun view(x: Double, y: Double): Point {
        val origin = canvas.contentOriginInView()
        return Point(origin.x + x.toInt(), origin.y + y.toInt())
    }

    private fun mouse(id: Int, at: Point, modifiers: Int = MouseEvent.BUTTON1_DOWN_MASK, clicks: Int = 1) =
        MouseEvent(canvas, id, System.currentTimeMillis(), modifiers, at.x, at.y, clicks, false, MouseEvent.BUTTON1)

    private fun drag(from: Point, to: Point, modifiers: Int = MouseEvent.BUTTON1_DOWN_MASK) {
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_PRESSED, from, modifiers))
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_DRAGGED, to, modifiers))
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_RELEASED, to, modifiers))
    }

    // --- 移動 ----------------------------------------------------------------

    fun `test dragging a shape reports its new position`() {
        drag(view(50.0, 40.0), view(150.0, 90.0))

        assertEquals(1, recorded.bounds.size)
        val (id, bounds, isResize) = recorded.bounds.single()
        assertEquals("first", id)
        assertFalse("移動であって大きさ変更ではない", isResize)
        assertEquals(100.0, bounds.x, 1.0)
        assertEquals(50.0, bounds.y, 1.0)
        assertEquals("大きさは変わらない", 100.0, bounds.width, 0.01)
    }

    fun `test a drag that does not move anything reports nothing`() {
        val at = view(50.0, 40.0)
        drag(at, at)

        assertTrue(recorded.bounds.isEmpty())
    }

    // --- 大きさ変更ーーーーー -------------------------------------------------

    fun `test dragging a corner handle resizes the shape`() {
        // まず選択してつまみを出す
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_PRESSED, view(50.0, 40.0)))
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_RELEASED, view(50.0, 40.0)))
        recorded.bounds.clear()

        // 右下のつまみ (100, 80) を (160, 130) へ
        drag(view(100.0, 80.0), view(160.0, 130.0))

        val (id, bounds, isResize) = recorded.bounds.single()
        assertEquals("first", id)
        assertTrue("大きさ変更として届く", isResize)
        assertEquals(160.0, bounds.width, 1.0)
        assertEquals(130.0, bounds.height, 1.0)
    }

    // --- 接続 ----------------------------------------------------------------

    fun `test dragging from the connect handle links two shapes`() {
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_PRESSED, view(50.0, 40.0)))
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_RELEASED, view(50.0, 40.0)))

        // 線を引くつまみは右辺の外側
        val handle = BpmnHandles.center(BpmnBounds(0.0, 0.0, 100.0, 80.0), BpmnHandle.CONNECT)
        drag(view(handle.first, handle.second), view(350.0, 240.0))

        assertEquals(listOf("first" to "second"), recorded.connections)
    }

    fun `test shift dragging also links two shapes`() {
        val shift = MouseEvent.BUTTON1_DOWN_MASK or MouseEvent.SHIFT_DOWN_MASK
        drag(view(50.0, 40.0), view(350.0, 240.0), modifiers = shift)

        assertEquals(listOf("first" to "second"), recorded.connections)
        assertTrue("線を引いたので移動にはならない", recorded.bounds.isEmpty())
    }

    fun `test releasing a connection over empty space links nothing`() {
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_PRESSED, view(50.0, 40.0)))
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_RELEASED, view(50.0, 40.0)))
        val handle = BpmnHandles.center(BpmnBounds(0.0, 0.0, 100.0, 80.0), BpmnHandle.CONNECT)

        drag(view(handle.first, handle.second), view(200.0, 400.0))

        assertTrue(recorded.connections.isEmpty())
    }

    // --- 追加 ----------------------------------------------------------------

    fun `test placing a palette item reports where it goes`() {
        canvas.armedPaletteItem = BpmnPaletteItem.SERVICE_TASK

        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_PRESSED, view(250.0, 150.0)))

        val created = recorded.created.single()
        assertEquals(BpmnPaletteItem.SERVICE_TASK, created.item)
        assertNull("何も無いところなのでコンテナ指定は無い", created.containerId)
        // 押した位置が図形の中心に来る
        assertEquals(250.0, created.bounds.centerX, 1.0)
        assertEquals(150.0, created.bounds.centerY, 1.0)
        assertNull("置いたら道具は外れる", canvas.armedPaletteItem)
    }

    fun `test a boundary event lands on the activity it is dropped on`() {
        canvas.armedPaletteItem = BpmnPaletteItem.BOUNDARY_TIMER_EVENT

        // first は (0,0)-(100,80)。その上を押す。
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_PRESSED, view(60.0, 40.0)))

        val created = recorded.created.single()
        assertEquals("first", created.attachToId)
        // 貼り付け先の下辺に寄る
        assertEquals(80.0, created.bounds.centerY, 1.0)
        assertNull("置いたら道具は外れる", canvas.armedPaletteItem)
    }

    fun `test a boundary event dropped on empty space is not placed`() {
        canvas.armedPaletteItem = BpmnPaletteItem.BOUNDARY_TIMER_EVENT

        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_PRESSED, view(600.0, 500.0)))

        assertTrue("貼り付け先が無いので置かない", recorded.created.isEmpty())
        assertEquals(
            "道具は構えたままで、もう一度狙える",
            BpmnPaletteItem.BOUNDARY_TIMER_EVENT,
            canvas.armedPaletteItem,
        )
    }

    // --- 削除 ----------------------------------------------------------------

    fun `test the delete key removes the selected shape`() {
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_PRESSED, view(50.0, 40.0)))
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_RELEASED, view(50.0, 40.0)))

        val stroke = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)
        val actionKey = canvas.getInputMap().get(stroke)
        assertNotNull("Delete が割り当てられている", actionKey)
        canvas.actionMap.get(actionKey).actionPerformed(null)

        assertEquals(listOf(listOf("first")), recorded.deleted)
    }

    // --- 読み取り専用 --------------------------------------------------------

    fun `test nothing is editable without an edit listener`() {
        canvas.editListener = null

        drag(view(50.0, 40.0), view(150.0, 90.0))
        canvas.armedPaletteItem = BpmnPaletteItem.USER_TASK
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_PRESSED, view(250.0, 150.0)))

        assertTrue(recorded.bounds.isEmpty())
        assertTrue(recorded.created.isEmpty())
    }

    // --- つまみの幾何 --------------------------------------------------------

    fun `test resizing from each corner keeps the opposite corner fixed`() {
        val start = BpmnBounds(100.0, 100.0, 100.0, 80.0)

        val topLeft = BpmnHandles.resize(start, BpmnHandle.TOP_LEFT, 60.0, 50.0)
        assertEquals(60.0, topLeft.x)
        assertEquals(50.0, topLeft.y)
        assertEquals("右下は動かない", 200.0, topLeft.right)
        assertEquals(180.0, topLeft.bottom)

        val bottomRight = BpmnHandles.resize(start, BpmnHandle.BOTTOM_RIGHT, 260.0, 230.0)
        assertEquals("左上は動かない", 100.0, bottomRight.x)
        assertEquals(160.0, bottomRight.width)
        assertEquals(130.0, bottomRight.height)
    }

    fun `test a shape cannot be collapsed by dragging past the opposite side`() {
        val start = BpmnBounds(100.0, 100.0, 100.0, 80.0)

        val squashed = BpmnHandles.resize(start, BpmnHandle.BOTTOM_RIGHT, 20.0, 20.0)

        assertTrue("幅が潰れない", squashed.width >= 16.0)
        assertTrue("高さが潰れない", squashed.height >= 16.0)
        assertEquals("左上は動かない", 100.0, squashed.x)
    }

    // --- 見た目 --------------------------------------------------------------

    fun `test the selected shape shows its handles`() {
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_PRESSED, view(50.0, 40.0)))
        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_RELEASED, view(50.0, 40.0)))

        val origin = canvas.contentOriginInView()
        val image = BufferedImage(origin.x + 200, origin.y + 160, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            canvas.size = Dimension(image.width, image.height)
            canvas.paint(g)
        } finally {
            g.dispose()
        }
        val output = File("build/reports/bpmn-render/canvas-handles.png")
        output.parentFile.mkdirs()
        ImageIO.write(image, "PNG", output)

        // つまみは選択色で描かれる。角と、線を引く丸の分だけ現れる。
        val selection = BpmnColors.SELECTION.rgb
        var found = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) == selection) found++
            }
        }
        assertTrue("選択中の図形につまみが描かれる (見つかった画素: $found)", found > 30)
    }

    // --- 線の折れ点 ----------------------------------------------------------

    /** 折れ点を持つ線を 1 本だけ載せた図に差し替える。 */
    private fun withBentEdge(): BpmnEdge {
        val edge = BpmnEdge(
            id = "flow1",
            name = null,
            sourceRef = "first",
            targetRef = "second",
            kind = BpmnConnectionKind.SEQUENCE_FLOW,
            waypoints = listOf(BpmnPoint(100.0, 40.0), BpmnPoint(200.0, 40.0), BpmnPoint(300.0, 240.0)),
        )
        canvas.setDiagram(
            BpmnDiagram(
                nodes = listOf(node("first", 0.0, 0.0), node("second", 300.0, 200.0)),
                edges = listOf(edge),
                hasDiagramInterchange = true,
            ),
            fit = false,
        )
        canvas.select(edge, scroll = false)
        return edge
    }

    fun `test dragging a bend point moves it`() {
        withBentEdge()

        drag(view(200.0, 40.0), view(220.0, 120.0))

        val (id, points) = recorded.waypoints.single()
        assertEquals("flow1", id)
        assertEquals("折れ点の数は変わらない", 3, points.size)
        assertEquals(220.0, points[1].x, 1.0)
        assertEquals(120.0, points[1].y, 1.0)
    }

    fun `test dragging the middle of a segment adds a bend point`() {
        withBentEdge()

        // 最初の線分 (100,40)-(200,40) の中点は (150,40)
        drag(view(150.0, 40.0), view(150.0, 130.0))

        val (_, points) = recorded.waypoints.single()
        assertEquals("折れ点が 1 つ増える", 4, points.size)
        assertEquals(150.0, points[1].x, 1.0)
        assertEquals(130.0, points[1].y, 1.0)
    }

    fun `test double clicking a bend point removes it`() {
        withBentEdge()

        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_CLICKED, view(200.0, 40.0), clicks = 2))

        val (_, points) = recorded.waypoints.single()
        assertEquals("折れ点が 1 つ減る", 2, points.size)
    }

    fun `test the last two points are never removed`() {
        val edge = BpmnEdge(
            id = "flow1",
            name = null,
            sourceRef = "first",
            targetRef = "second",
            kind = BpmnConnectionKind.SEQUENCE_FLOW,
            waypoints = listOf(BpmnPoint(100.0, 40.0), BpmnPoint(300.0, 240.0)),
        )
        canvas.setDiagram(
            BpmnDiagram(
                nodes = listOf(node("first", 0.0, 0.0), node("second", 300.0, 200.0)),
                edges = listOf(edge),
                hasDiagramInterchange = true,
            ),
            fit = false,
        )
        canvas.select(edge, scroll = false)

        canvas.dispatchEvent(mouse(MouseEvent.MOUSE_CLICKED, view(100.0, 40.0), clicks = 2))

        assertTrue("両端は消せない", recorded.waypoints.isEmpty())
    }
}
