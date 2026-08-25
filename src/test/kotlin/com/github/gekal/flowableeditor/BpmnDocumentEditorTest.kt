package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.edit.BpmnDocumentEditor
import com.github.gekal.flowableeditor.edit.BpmnPaletteItem
import com.github.gekal.flowableeditor.model.BpmnBounds
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnModelParser
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * 図の編集が XML にどう書き戻るかを見る。
 *
 * 描画より先にここを固めておく。座標だけの変更であってもフローや図形情報を
 * 取りこぼすと壊れた定義になり、画面を見ているだけでは気付けないため。
 */
class BpmnDocumentEditorTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun open(fileName: String): Pair<XmlFile, BpmnDiagram> {
        myFixture.configureByFile(fileName)
        val file = myFixture.file as XmlFile
        return file to BpmnModelParser.parse(file)
    }

    private fun reparse(file: XmlFile): BpmnDiagram = BpmnModelParser.parse(file)

    // --- 位置と大きさ --------------------------------------------------------

    fun `test moving a shape writes the new coordinates back`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        BpmnDocumentEditor.setBounds(
            project,
            file,
            diagram,
            mapOf("approve" to BpmnBounds(300.0, 200.0, 100.0, 80.0)),
            "move",
        )

        val moved = reparse(file).nodesById.getValue("approve").bounds!!
        assertEquals(300.0, moved.x)
        assertEquals(200.0, moved.y)
        assertTrue(file.text.contains("""x="300""""))
    }

    fun `test resizing writes width and height`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        BpmnDocumentEditor.setBounds(
            project,
            file,
            diagram,
            mapOf("approve" to BpmnBounds(180.0, 75.0, 160.0, 120.0)),
            "resize",
        )

        val resized = reparse(file).nodesById.getValue("approve").bounds!!
        assertEquals(160.0, resized.width)
        assertEquals(120.0, resized.height)
    }

    fun `test editing a definition without diagram interchange materialises it`() {
        val (file, diagram) = open("subProcessWithoutDi.bpmn20.xml")
        assertFalse("元のファイルは図形情報を持たない", diagram.hasDiagramInterchange)

        BpmnDocumentEditor.setBounds(
            project,
            file,
            diagram,
            mapOf("review" to BpmnBounds(500.0, 400.0, 240.0, 160.0)),
            "move",
        )

        val updated = reparse(file)
        assertTrue("図形情報が書き出される", updated.hasDiagramInterchange)
        // 自動レイアウトで見えていた他の要素も、そのままの位置で残る
        assertNotNull(updated.nodesById.getValue("start").bounds)
        assertNotNull(updated.nodesById.getValue("end").bounds)
        val moved = updated.nodesById.getValue("review").bounds!!
        assertEquals(500.0, moved.x)
        assertEquals(400.0, moved.y)
    }

    // --- 名前 ----------------------------------------------------------------

    fun `test renaming writes the name attribute`() {
        val (file, _) = open("orderProcessWithDi.bpmn20.xml")

        BpmnDocumentEditor.setName(project, file, "approve", "承認する", "rename")

        assertEquals("承認する", reparse(file).nodesById.getValue("approve").name)
    }

    fun `test clearing the name removes the attribute`() {
        val (file, _) = open("orderProcessWithDi.bpmn20.xml")

        BpmnDocumentEditor.setName(project, file, "approve", "", "rename")

        assertNull(reparse(file).nodesById.getValue("approve").name)
    }

    // --- 追加 ----------------------------------------------------------------

    fun `test adding an element from the palette`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        val id = BpmnDocumentEditor.createElement(
            project,
            file,
            diagram,
            BpmnPaletteItem.SERVICE_TASK,
            BpmnBounds(600.0, 300.0, 100.0, 80.0),
            containerId = "orderProcess",
            commandName = "add",
        )

        assertNotNull(id)
        val updated = reparse(file)
        val added = updated.nodesById.getValue(id!!)
        assertEquals("serviceTask", added.tagName)
        assertEquals("orderProcess", added.parentId)
        assertEquals(600.0, added.bounds!!.x)
    }

    fun `test an added element gets an id that is not taken`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        val first = BpmnDocumentEditor.createElement(
            project, file, reparse(file), BpmnPaletteItem.USER_TASK,
            BpmnBounds(600.0, 300.0, 100.0, 80.0), "orderProcess", "add",
        )
        val second = BpmnDocumentEditor.createElement(
            project, file, reparse(file), BpmnPaletteItem.USER_TASK,
            BpmnBounds(600.0, 400.0, 100.0, 80.0), "orderProcess", "add",
        )

        assertNotNull(first)
        assertNotNull(second)
        assertFalse("id が衝突しない", first == second)
        assertEquals(2, reparse(file).nodes.count { it.tagName == "userTask" && it.id != "approve" })
        // 未使用の diagram 引数を明示的に使わないための確認
        assertNotNull(diagram)
    }

    fun `test adding a timer event also writes its event definition`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        val id = BpmnDocumentEditor.createElement(
            project, file, diagram, BpmnPaletteItem.TIMER_CATCH_EVENT,
            BpmnBounds(600.0, 300.0, 36.0, 36.0), "orderProcess", "add",
        )

        assertEquals("timer", reparse(file).nodesById.getValue(id!!).eventDefinition)
    }

    // --- 接続 ----------------------------------------------------------------

    fun `test connecting two elements inserts a sequence flow`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        val id = BpmnDocumentEditor.connect(project, file, diagram, "ship", "start", "connect")

        assertNotNull(id)
        val flow = reparse(file).edges.first { it.id == id }
        assertEquals("ship", flow.sourceRef)
        assertEquals("start", flow.targetRef)
        assertTrue("線の折れ点も書かれる", flow.waypoints.size >= 2)
    }

    fun `test connecting the same pair twice does nothing`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")
        val before = reparse(file).edges.size

        // start -> approve はすでに flow1 で結ばれている
        val id = BpmnDocumentEditor.connect(project, file, diagram, "start", "approve", "connect")

        assertNull(id)
        assertEquals(before, reparse(file).edges.size)
    }

    fun `test an element cannot be connected to itself`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        assertNull(BpmnDocumentEditor.connect(project, file, diagram, "approve", "approve", "connect"))
    }

    // --- 削除 ----------------------------------------------------------------

    fun `test deleting an element also removes the flows that touch it`() {
        val (file, _) = open("orderProcessWithDi.bpmn20.xml")

        BpmnDocumentEditor.delete(project, file, listOf("approve"), "delete")

        val updated = reparse(file)
        assertNull(updated.nodesById["approve"])
        assertTrue(
            "approve に触れるフローが残っていない",
            updated.edges.none { it.sourceRef == "approve" || it.targetRef == "approve" },
        )
        assertFalse("図形情報も消える", file.text.contains("bpmnElement=\"approve\""))
    }

    fun `test deleting an activity also removes its boundary events`() {
        val (file, _) = open("subProcessWithoutDi.bpmn20.xml")

        BpmnDocumentEditor.delete(project, file, listOf("review"), "delete")

        val updated = reparse(file)
        assertNull(updated.nodesById["review"])
        assertNull("貼り付いていた境界イベントも消える", updated.nodesById["timeout"])
        assertTrue(
            "境界イベント発のフローも残らない",
            updated.edges.none { it.sourceRef == "timeout" },
        )
    }

    fun `test deleting leaves the rest of the process untouched`() {
        val (file, _) = open("orderProcessWithDi.bpmn20.xml")

        BpmnDocumentEditor.delete(project, file, listOf("ship"), "delete")

        val updated = reparse(file)
        assertNotNull(updated.nodesById["start"])
        assertNotNull(updated.nodesById["approve"])
        assertNotNull(updated.nodesById["decision"])
        assertNotNull(updated.nodesById["end"])
        // decision -> end (flow4) は ship と無関係なので残る
        assertTrue(updated.edges.any { it.sourceRef == "decision" && it.targetRef == "end" })
    }

    // --- 線の追従 ------------------------------------------------------------

    fun `test moving a shape drags its connections along`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")
        val before = reparse(file).edges.first { it.id == "flow1" }.waypoints

        // start -> approve の approve を大きく動かす
        BpmnDocumentEditor.setBounds(
            project, file, diagram,
            mapOf("approve" to BpmnBounds(180.0, 400.0, 100.0, 80.0)),
            "move",
        )

        val after = reparse(file).edges.first { it.id == "flow1" }.waypoints
        assertTrue("線が引き直される", after != before)

        // 線の終点が、動かした先の図形の縁に付いている
        val moved = reparse(file).nodesById.getValue("approve").bounds!!
        val end = after.last()
        assertTrue(
            "終点が図形の縁にある (end=$end, bounds=$moved)",
            end.x >= moved.x - 1 && end.x <= moved.right + 1 &&
                end.y >= moved.y - 1 && end.y <= moved.bottom + 1,
        )
    }

    fun `test both ends follow when either shape moves`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        // flow1 の始点側 (start) を動かす
        BpmnDocumentEditor.setBounds(
            project, file, diagram,
            mapOf("start" to BpmnBounds(50.0, 500.0, 30.0, 30.0)),
            "move",
        )

        val updated = reparse(file)
        val start = updated.nodesById.getValue("start").bounds!!
        val begin = updated.edges.first { it.id == "flow1" }.waypoints.first()
        assertTrue(
            "始点が動かした先の図形に付いてくる (begin=$begin, bounds=$start)",
            begin.y > 400,
        )
    }

    fun `test resizing also drags the connections`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")
        val before = reparse(file).edges.first { it.id == "flow1" }.waypoints.last()

        BpmnDocumentEditor.setBounds(
            project, file, diagram,
            mapOf("approve" to BpmnBounds(180.0, 75.0, 300.0, 200.0)),
            "resize",
        )

        val after = reparse(file).edges.first { it.id == "flow1" }.waypoints.last()
        assertTrue("大きさを変えても端が付き直される", after != before)
    }

    fun `test untouched connections are left alone`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")
        val before = reparse(file).edges.first { it.id == "flow4" }.waypoints

        // flow4 は decision -> end。approve とは無関係。
        BpmnDocumentEditor.setBounds(
            project, file, diagram,
            mapOf("approve" to BpmnBounds(180.0, 400.0, 100.0, 80.0)),
            "move",
        )

        assertEquals("関係ない線は触らない", before, reparse(file).edges.first { it.id == "flow4" }.waypoints)
    }

    fun `test hand placed bends are kept when a shape moves`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")
        // flow1 に折れ点を足した状態を作る
        BpmnDocumentEditor.setBounds(project, file, diagram, mapOf("start" to BpmnBounds(100.0, 100.0, 30.0, 30.0)), "seed")

        val seeded = reparse(file)
        val threePoints = listOf(
            seeded.edges.first { it.id == "flow1" }.waypoints.first(),
            com.github.gekal.flowableeditor.model.BpmnPoint(155.0, 300.0),
            seeded.edges.first { it.id == "flow1" }.waypoints.last(),
        )
        seeded.edges.first { it.id == "flow1" }.waypoints = threePoints
        BpmnDocumentEditor.connect(project, file, seeded, "end", "start", "connect")

        // 折れ点を保つ挙動は幾何側で確かめる
        val rerouted = com.github.gekal.flowableeditor.model.BpmnGeometry.reroute(
            threePoints,
            BpmnBounds(0.0, 0.0, 40.0, 40.0),
            BpmnBounds(300.0, 300.0, 40.0, 40.0),
        )
        assertEquals("途中の折れ点は残る", 3, rerouted.size)
        assertEquals(threePoints[1], rerouted[1])
    }

    // --- 境界イベント --------------------------------------------------------

    fun `test a boundary event is attached to the activity it is dropped on`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        val id = BpmnDocumentEditor.createElement(
            project, file, diagram, BpmnPaletteItem.BOUNDARY_TIMER_EVENT,
            BpmnBounds(230.0, 140.0, 36.0, 36.0),
            containerId = "approve",
            commandName = "add",
            attachToId = "approve",
        )

        assertNotNull(id)
        val added = reparse(file).nodesById.getValue(id!!)
        assertEquals("boundaryEvent", added.tagName)
        assertEquals("貼り付け先が記録される", "approve", added.attachedToRef)
        assertEquals("timer", added.eventDefinition)
        // 境界イベントは貼り付け先の中ではなく隣に置かれる
        assertEquals("orderProcess", added.parentId)
    }

    fun `test a boundary event is not created without a host`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")
        val before = reparse(file).nodes.size

        val id = BpmnDocumentEditor.createElement(
            project, file, diagram, BpmnPaletteItem.BOUNDARY_TIMER_EVENT,
            BpmnBounds(600.0, 400.0, 36.0, 36.0),
            containerId = null, commandName = "add", attachToId = null,
        )

        assertNull(id)
        assertEquals(before, reparse(file).nodes.size)
    }

    fun `test deleting the host also removes the boundary event that was added`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")
        val id = BpmnDocumentEditor.createElement(
            project, file, diagram, BpmnPaletteItem.BOUNDARY_TIMER_EVENT,
            BpmnBounds(230.0, 140.0, 36.0, 36.0), "approve", "add", attachToId = "approve",
        )

        BpmnDocumentEditor.delete(project, file, listOf("approve"), "delete")

        val updated = reparse(file)
        assertNull(updated.nodesById["approve"])
        assertNull("貼り付いていた境界イベントも消える", updated.nodesById[id!!])
    }
}
