package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.edit.BpmnDocumentEditor
import com.github.gekal.flowableeditor.edit.BpmnPaletteItem
import com.github.gekal.flowableeditor.model.BpmnAutoLayout
import com.github.gekal.flowableeditor.model.BpmnBounds
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnElementKind
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
        myFixture.configureFromTestData(fileName)
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
        BpmnDocumentEditor.setBounds(
            project, file, diagram,
            mapOf("start" to BpmnBounds(100.0, 100.0, 30.0, 30.0)),
            "seed",
        )

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

    // --- 折れ点の書き戻し ----------------------------------------------------

    fun `test bend points are written back and the ends stay docked`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        BpmnDocumentEditor.setWaypoints(
            project, file, diagram, "flow1",
            listOf(
                com.github.gekal.flowableeditor.model.BpmnPoint(130.0, 115.0),
                com.github.gekal.flowableeditor.model.BpmnPoint(155.0, 260.0),
                com.github.gekal.flowableeditor.model.BpmnPoint(180.0, 115.0),
            ),
            "bend",
        )

        val points = reparse(file).edges.first { it.id == "flow1" }.waypoints
        assertEquals("途中の折れ点が残る", 3, points.size)
        assertEquals(155.0, points[1].x, 1.0)
        assertEquals(260.0, points[1].y, 1.0)

        // 両端は図形の縁に付け直される
        val start = reparse(file).nodesById.getValue("start").bounds!!
        assertTrue(
            "始点が図形の縁にある",
            points.first().x >= start.x - 1 && points.first().x <= start.right + 1,
        )
    }

    fun `test a line needs at least two points`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")
        val before = reparse(file).edges.first { it.id == "flow1" }.waypoints

        BpmnDocumentEditor.setWaypoints(
            project, file, diagram, "flow1",
            listOf(com.github.gekal.flowableeditor.model.BpmnPoint(1.0, 1.0)),
            "bend",
        )

        assertEquals(before, reparse(file).edges.first { it.id == "flow1" }.waypoints)
    }

    // --- プールとレーン ------------------------------------------------------

    fun `test pools and lanes are part of the diagram`() {
        val (_, diagram) = open("collaborationWithLanes.bpmn20.xml")

        assertEquals(BpmnElementKind.POOL, diagram.nodesById.getValue("pool1").kind)
        assertEquals(BpmnElementKind.LANE, diagram.nodesById.getValue("lane1").kind)
        assertNotNull("図形情報がある", diagram.nodesById.getValue("pool1").bounds)
    }

    fun `test a lane can be moved and resized like any other shape`() {
        val (file, diagram) = open("collaborationWithLanes.bpmn20.xml")

        BpmnDocumentEditor.setBounds(
            project, file, diagram,
            mapOf("lane2" to BpmnBounds(130.0, 240.0, 570.0, 160.0)),
            "resize",
        )

        val lane = reparse(file).nodesById.getValue("lane2").bounds!!
        assertEquals(240.0, lane.y)
        assertEquals(160.0, lane.height)
    }

    fun `test renaming a lane writes its name`() {
        val (file, _) = open("collaborationWithLanes.bpmn20.xml")

        BpmnDocumentEditor.setName(project, file, "lane2", "Director", "rename")

        assertEquals("Director", reparse(file).nodesById.getValue("lane2").name)
    }

    fun `test moving a pool carries everything inside it`() {
        val (file, diagram) = open("collaborationWithLanes.bpmn20.xml")
        val before = reparse(file).nodesById.getValue("start").bounds!!

        // プールを下へ 300 動かす
        BpmnDocumentEditor.setBounds(
            project, file, diagram,
            mapOf("pool1" to BpmnBounds(100.0, 400.0, 600.0, 250.0)),
            "move",
        )

        val updated = reparse(file)
        assertEquals(400.0, updated.nodesById.getValue("pool1").bounds!!.y)
        assertEquals("中の要素も同じだけ動く", before.y + 300.0, updated.nodesById.getValue("start").bounds!!.y)
        assertEquals("横はそのまま", before.x, updated.nodesById.getValue("start").bounds!!.x)
        assertEquals("レーンも付いてくる", 400.0, updated.nodesById.getValue("lane1").bounds!!.y)
    }

    fun `test moving a lane carries only what is in that lane`() {
        val (file, diagram) = open("collaborationWithLanes.bpmn20.xml")
        val poolBefore = reparse(file).nodesById.getValue("pool1").bounds!!

        // lane1 (上の帯) を右へ 50 動かす。start は lane1 の中にある。
        BpmnDocumentEditor.setBounds(
            project, file, diagram,
            mapOf("lane1" to BpmnBounds(180.0, 100.0, 570.0, 125.0)),
            "move",
        )

        val updated = reparse(file)
        assertEquals("レーンの中の要素は付いてくる", 230.0, updated.nodesById.getValue("start").bounds!!.x)
        assertEquals("プールは動かない", poolBefore, updated.nodesById.getValue("pool1").bounds)
    }

    fun `test resizing a container leaves its contents where they are`() {
        val (file, diagram) = open("collaborationWithLanes.bpmn20.xml")
        val before = reparse(file).nodesById.getValue("start").bounds!!

        BpmnDocumentEditor.setBounds(
            project, file, diagram,
            mapOf("pool1" to BpmnBounds(100.0, 100.0, 800.0, 320.0)),
            "resize",
        )

        assertEquals("大きさを変えただけなら中身は動かない", before, reparse(file).nodesById.getValue("start").bounds)
    }

    fun `test moving an activity carries its boundary event`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")
        val id = BpmnDocumentEditor.createElement(
            project, file, diagram, BpmnPaletteItem.BOUNDARY_TIMER_EVENT,
            BpmnBounds(230.0, 137.0, 36.0, 36.0), "approve", "add", attachToId = "approve",
        )
        val before = reparse(file).nodesById.getValue(id!!).bounds!!

        BpmnDocumentEditor.setBounds(
            project, file, reparse(file),
            mapOf("approve" to BpmnBounds(180.0, 375.0, 100.0, 80.0)),
            "move",
        )

        val moved = reparse(file).nodesById.getValue(id).bounds!!
        assertEquals("境界イベントも同じだけ動く", before.y + 300.0, moved.y)
    }

    // --- 自動整列 ------------------------------------------------------------

    fun `test rearranging lays the diagram out from left to right`() {
        val (file, _) = open("orderProcessWithDi.bpmn20.xml")
        // わざと崩す
        BpmnDocumentEditor.setBounds(
            project, file, reparse(file),
            mapOf("approve" to BpmnBounds(20.0, 600.0, 100.0, 80.0)),
            "move",
        )

        val arranged = reparse(file)
        BpmnAutoLayout.relayout(arranged)
        BpmnDocumentEditor.applyLayout(project, file, arranged, "layout")

        val updated = reparse(file)
        val start = updated.nodesById.getValue("start").bounds!!
        val approve = updated.nodesById.getValue("approve").bounds!!
        val end = updated.nodesById.getValue("end").bounds!!
        assertTrue("流れの順に左から並ぶ", start.x < approve.x)
        assertTrue(approve.x < end.x)
    }

    fun `test rearranging redraws the connections`() {
        val (file, _) = open("orderProcessWithDi.bpmn20.xml")
        val arranged = reparse(file)
        BpmnAutoLayout.relayout(arranged)
        BpmnDocumentEditor.applyLayout(project, file, arranged, "layout")

        val updated = reparse(file)
        for (edge in updated.edges) {
            assertTrue("${edge.id} に折れ点がある", edge.waypoints.size >= 2)
        }
        // 端が図形の縁の近くに来ている
        val flow = updated.edges.first { it.id == "flow1" }
        val startBounds = updated.nodesById.getValue("start").bounds!!
        assertTrue(
            "始点が図形に接している",
            flow.waypoints.first().x >= startBounds.x - 1 &&
                flow.waypoints.first().x <= startBounds.right + 1,
        )
    }

    fun `test rearranging is refused when the diagram has pools`() {
        val (_, diagram) = open("collaborationWithLanes.bpmn20.xml")

        assertFalse("プールがある図は整列しない", BpmnAutoLayout.canRelayout(diagram))

        val before = diagram.nodesById.getValue("start").bounds
        BpmnAutoLayout.relayout(diagram)
        assertEquals("何も動かさない", before, diagram.nodesById.getValue("start").bounds)
    }
}
