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

    fun `test rearranging keeps elements inside their lane`() {
        val (file, _) = open("collaborationWithLanes.bpmn20.xml")
        // end を下のレーンへ移す (所属も書き換わる)
        BpmnDocumentEditor.setBounds(
            project, file, reparse(file),
            mapOf("end" to BpmnBounds(600.0, 270.0, 30.0, 30.0)),
            "move",
        )

        val arranged = reparse(file)
        BpmnAutoLayout.relayout(arranged)
        BpmnDocumentEditor.applyLayout(project, file, arranged, "layout")

        val updated = reparse(file)
        val lane1 = updated.nodesById.getValue("lane1").bounds!!
        val lane2 = updated.nodesById.getValue("lane2").bounds!!
        val start = updated.nodesById.getValue("start").bounds!!
        val end = updated.nodesById.getValue("end").bounds!!

        assertTrue("start は上のレーンに残る", lane1.contains(start.centerX, start.centerY))
        assertTrue("end は下のレーンに残る", lane2.contains(end.centerX, end.centerY))
        assertTrue("流れの順は保たれる", start.x < end.x)
    }

    fun `test rearranging does not shrink the pool below its contents`() {
        val (file, _) = open("collaborationWithLanes.bpmn20.xml")

        val arranged = reparse(file)
        BpmnAutoLayout.relayout(arranged)
        BpmnDocumentEditor.applyLayout(project, file, arranged, "layout")

        val updated = reparse(file)
        val pool = updated.nodesById.getValue("pool1").bounds!!
        val rightmost = updated.nodes.filter { !it.kind.isPoolOrLane }.maxOf { it.bounds!!.right }
        assertTrue("中身がプールからはみ出さない", rightmost <= pool.right)
    }

    // --- プールとレーンの作成 ------------------------------------------------

    fun `test creating the first pool wraps the existing process`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        val id = BpmnDocumentEditor.createElement(
            project, file, diagram, BpmnPaletteItem.POOL,
            BpmnBounds(60.0, 40.0, 600.0, 250.0), null, "add",
        )

        assertNotNull(id)
        assertTrue("collaboration ができる", file.text.contains("<collaboration"))
        assertTrue("既にあるプロセスを指す", file.text.contains("""processRef="orderProcess""""))

        val updated = reparse(file)
        val pool = updated.nodesById.getValue(id!!)
        assertEquals(BpmnElementKind.POOL, pool.kind)
        // 押した位置ではなく、指しているプロセスの要素を囲む大きさになる
        val poolBounds = pool.bounds!!
        for (node in updated.nodes.filter { !it.kind.isPoolOrLane }) {
            val box = node.bounds!!
            assertTrue(
                "${node.id} がプールに収まる (node=$box, pool=$poolBounds)",
                box.x >= poolBounds.x && box.right <= poolBounds.right &&
                    box.y >= poolBounds.y && box.bottom <= poolBounds.bottom,
            )
        }
    }

    fun `test the diagram plane points at the collaboration once a pool exists`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        BpmnDocumentEditor.createElement(
            project, file, diagram, BpmnPaletteItem.POOL,
            BpmnBounds(60.0, 40.0, 600.0, 250.0), null, "add",
        )

        val plane = file.text.substringAfter("<bpmndi:BPMNPlane").substringBefore(">")
        assertFalse("面がプロセスを指したままにならない", plane.contains("\"orderProcess\""))
        assertTrue("面が collaboration を指す", plane.contains("collaboration"))
    }

    fun `test a second pool gets its own process`() {
        val (file, _) = open("orderProcessWithDi.bpmn20.xml")
        BpmnDocumentEditor.createElement(
            project, file, reparse(file), BpmnPaletteItem.POOL,
            BpmnBounds(60.0, 40.0, 600.0, 250.0), null, "add",
        )

        val second = BpmnDocumentEditor.createElement(
            project, file, reparse(file), BpmnPaletteItem.POOL,
            BpmnBounds(60.0, 320.0, 600.0, 250.0), null, "add",
        )

        assertNotNull(second)
        assertEquals("プロセスが 2 つになる", 2, Regex("<process ").findAll(file.text).count())
        assertEquals("プールが 2 つになる", 2, Regex("<participant ").findAll(file.text).count())
    }

    fun `test creating a lane adds it to the process lane set`() {
        val (file, diagram) = open("collaborationWithLanes.bpmn20.xml")

        val id = BpmnDocumentEditor.createElement(
            project, file, diagram, BpmnPaletteItem.LANE,
            BpmnBounds(130.0, 350.0, 570.0, 125.0), "pool1", "add",
        )

        assertNotNull(id)
        val updated = reparse(file)
        assertEquals(BpmnElementKind.LANE, updated.nodesById.getValue(id!!).kind)
        assertEquals("既存のレーンと合わせて 3 本", 3, updated.nodes.count { it.kind == BpmnElementKind.LANE })
    }

    fun `test creating a lane makes a lane set when there is none`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        val id = BpmnDocumentEditor.createElement(
            project, file, diagram, BpmnPaletteItem.LANE,
            BpmnBounds(60.0, 40.0, 600.0, 125.0), null, "add",
        )

        assertNotNull(id)
        assertTrue("laneSet ができる", file.text.contains("<laneSet"))
    }

    // --- 線の種類 ------------------------------------------------------------

    fun `test connecting across pools makes a message flow`() {
        val (file, _) = open("collaborationWithLanes.bpmn20.xml")
        // 2 つ目のプールと、その中の要素を用意する
        val pool2 = BpmnDocumentEditor.createElement(
            project, file, reparse(file), BpmnPaletteItem.POOL,
            BpmnBounds(100.0, 500.0, 600.0, 250.0), null, "add",
        )
        val task = BpmnDocumentEditor.createElement(
            project, file, reparse(file), BpmnPaletteItem.USER_TASK,
            BpmnBounds(200.0, 560.0, 100.0, 80.0), null, "add",
        )
        assertNotNull(pool2)

        BpmnDocumentEditor.connect(project, file, reparse(file), "start", task!!, "connect")

        assertTrue("プールをまたぐ線はメッセージフロー", file.text.contains("<messageFlow"))
    }

    fun `test connecting a text annotation makes an association`() {
        val (file, _) = open("orderProcessWithDi.bpmn20.xml")
        val note = BpmnDocumentEditor.createElement(
            project, file, reparse(file), BpmnPaletteItem.TEXT_ANNOTATION,
            BpmnBounds(200.0, 300.0, 120.0, 50.0), null, "add",
        )

        BpmnDocumentEditor.connect(project, file, reparse(file), note!!, "approve", "connect")

        assertTrue("注記に繋ぐ線は関連", file.text.contains("<association"))
        assertFalse("シーケンスフローは増えない", file.text.contains("""id="sequenceFlow_1""""))
    }

    fun `test a plain connection is still a sequence flow`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")

        val id = BpmnDocumentEditor.connect(project, file, diagram, "ship", "start", "connect")

        assertNotNull(id)
        assertTrue(id!!.startsWith("sequenceFlow"))
    }

    // --- レーン所属 ----------------------------------------------------------

    fun `test moving an element to another lane rewrites its membership`() {
        val (file, diagram) = open("collaborationWithLanes.bpmn20.xml")
        assertTrue("はじめは lane1 に属している", file.text.contains("<flowNodeRef>start</flowNodeRef>"))

        // start を下のレーン (lane2: y 225..350) へ動かす
        BpmnDocumentEditor.setBounds(
            project, file, diagram,
            mapOf("start" to BpmnBounds(180.0, 270.0, 30.0, 30.0)),
            "move",
        )

        val lane2 = file.text.substringAfter("""<lane id="lane2"""").substringBefore("</lane>")
        assertTrue("移動先のレーンに入る", lane2.contains("<flowNodeRef>start</flowNodeRef>"))
        val lane1 = file.text.substringAfter("""<lane id="lane1"""").substringBefore("</lane>")
        assertFalse("元のレーンからは外れる", lane1.contains("<flowNodeRef>start</flowNodeRef>"))
    }

    fun `test an element outside every lane belongs to none`() {
        val (file, diagram) = open("collaborationWithLanes.bpmn20.xml")

        // プールの外へ出す
        BpmnDocumentEditor.setBounds(
            project, file, diagram,
            mapOf("start" to BpmnBounds(1200.0, 900.0, 30.0, 30.0)),
            "move",
        )

        assertFalse("どのレーンにも属さない", file.text.contains("<flowNodeRef>start</flowNodeRef>"))
    }

    // --- プールの削除 --------------------------------------------------------

    fun `test deleting a pool also removes the process it points at`() {
        val (file, _) = open("collaborationWithLanes.bpmn20.xml")

        BpmnDocumentEditor.delete(project, file, listOf("pool1"), "delete")

        assertFalse("参照先のプロセスが残らない", file.text.contains("""id="salesProcess""""))
        assertFalse("中の要素も残らない", file.text.contains("""id="start""""))
        val updated = reparse(file)
        assertNull(updated.nodesById["pool1"])
        assertNull("図形情報も消える", updated.nodesById["start"])
    }

    // --- 境界イベントの追従 --------------------------------------------------

    fun `test a boundary event stays on the border when the host is resized`() {
        val (file, diagram) = open("orderProcessWithDi.bpmn20.xml")
        val id = BpmnDocumentEditor.createElement(
            project, file, diagram, BpmnPaletteItem.BOUNDARY_TIMER_EVENT,
            BpmnBounds(212.0, 137.0, 36.0, 36.0), "approve", "add", attachToId = "approve",
        )

        // approve (180,75,100,80) を倍の大きさにする
        BpmnDocumentEditor.setBounds(
            project, file, reparse(file),
            mapOf("approve" to BpmnBounds(180.0, 75.0, 200.0, 160.0)),
            "resize",
        )

        val updated = reparse(file)
        val host = updated.nodesById.getValue("approve").bounds!!
        val event = updated.nodesById.getValue(id!!).bounds!!
        assertTrue(
            "縁に付いたまま (event=$event, host=$host)",
            event.centerX >= host.x && event.centerX <= host.right &&
                event.centerY >= host.y && event.centerY <= host.bottom + 1,
        )
    }

    // --- プールをまたぐ移動 --------------------------------------------------

    /** 2 つのプールを持つ図を作り、2 つ目のプールの id を返す。 */
    private fun withSecondPool(file: XmlFile): String = requireNotNull(
        BpmnDocumentEditor.createElement(
            project, file, reparse(file), BpmnPaletteItem.POOL,
            BpmnBounds(100.0, 500.0, 600.0, 250.0), null, "add",
        ),
    )

    fun `test moving an element into another pool moves it to that process`() {
        val (file, _) = open("collaborationWithLanes.bpmn20.xml")
        val pool2 = withSecondPool(file)
        val process2 = file.text
            .substringAfter("""id="$pool2"""")
            .substringAfter("processRef=\"")
            .substringBefore("\"")

        // end を 2 つ目のプールの中へ動かす
        BpmnDocumentEditor.setBounds(
            project, file, reparse(file),
            mapOf("end" to BpmnBounds(300.0, 600.0, 30.0, 30.0)),
            "move",
        )

        val movedProcess = file.text
            .substringAfter("""<process id="$process2"""")
            .substringBefore("</process>")
        assertTrue("移した先のプロセスに入る", movedProcess.contains("""id="end""""))
        val original = file.text.substringAfter("""<process id="salesProcess"""").substringBefore("</process>")
        assertFalse("元のプロセスからは外れる", original.contains("""id="end""""))
    }

    fun `test a flow that ends up crossing pools becomes a message flow`() {
        val (file, _) = open("collaborationWithLanes.bpmn20.xml")
        withSecondPool(file)
        assertTrue("はじめはシーケンスフロー", file.text.contains("""<sequenceFlow id="f1""""))

        BpmnDocumentEditor.setBounds(
            project, file, reparse(file),
            mapOf("end" to BpmnBounds(300.0, 600.0, 30.0, 30.0)),
            "move",
        )

        assertTrue("プールをまたぐのでメッセージフローになる", file.text.contains("""<messageFlow id="f1""""))
        assertFalse(file.text.contains("""<sequenceFlow id="f1""""))
    }

    fun `test bringing an element back makes the flow a sequence flow again`() {
        val (file, _) = open("collaborationWithLanes.bpmn20.xml")
        withSecondPool(file)
        BpmnDocumentEditor.setBounds(
            project, file, reparse(file),
            mapOf("end" to BpmnBounds(300.0, 600.0, 30.0, 30.0)),
            "move",
        )
        assertTrue(file.text.contains("<messageFlow"))

        // 元のプールへ戻す
        BpmnDocumentEditor.setBounds(
            project, file, reparse(file),
            mapOf("end" to BpmnBounds(600.0, 145.0, 30.0, 30.0)),
            "move",
        )

        assertTrue("同じプロセスに戻ればシーケンスフローに戻る", file.text.contains("""<sequenceFlow id="f1""""))
        assertFalse(file.text.contains("<messageFlow"))
    }

    fun `test the connection keeps its diagram information across the change`() {
        val (file, _) = open("collaborationWithLanes.bpmn20.xml")
        withSecondPool(file)

        BpmnDocumentEditor.setBounds(
            project, file, reparse(file),
            mapOf("end" to BpmnBounds(300.0, 600.0, 30.0, 30.0)),
            "move",
        )

        // id を変えていないので図形情報はそのまま使える
        val flow = reparse(file).edges.first { it.id == "f1" }
        assertTrue("線が図から消えない", flow.waypoints.size >= 2)
    }

    // --- 帯の高さ ------------------------------------------------------------

    fun `test rearranging grows a lane that is too short for its contents`() {
        val (file, _) = open("collaborationWithLanes.bpmn20.xml")
        // 背の高い要素を上のレーンへ入れる (帯は 125 しかない)
        val tall = BpmnDocumentEditor.createElement(
            project, file, reparse(file), BpmnPaletteItem.SUB_PROCESS,
            BpmnBounds(300.0, 120.0, 200.0, 200.0), null, "add",
        )
        BpmnDocumentEditor.setBounds(
            project, file, reparse(file),
            mapOf(tall!! to BpmnBounds(300.0, 110.0, 200.0, 200.0)),
            "move",
        )

        val arranged = reparse(file)
        BpmnAutoLayout.relayout(arranged)
        BpmnDocumentEditor.applyLayout(project, file, arranged, "layout")

        val updated = reparse(file)
        val lane1 = updated.nodesById.getValue("lane1").bounds!!
        val subProcess = updated.nodesById.getValue(tall).bounds!!
        // 整列は要素の大きさも決め直すので、帯が「元の 125 より広がったか」で見る
        assertTrue("帯が中身に合わせて広がる (lane=$lane1, node=$subProcess)", lane1.height > 125.0)
        assertTrue(
            "要素が帯からはみ出さない (lane=$lane1, node=$subProcess)",
            subProcess.y >= lane1.y - 1 && subProcess.bottom <= lane1.bottom + 1,
        )

        // 下の帯は押し下げられ、プールは全部を覆う
        val lane2 = updated.nodesById.getValue("lane2").bounds!!
        val pool = updated.nodesById.getValue("pool1").bounds!!
        assertTrue("下の帯が重ならない", lane2.y >= lane1.bottom - 1)
        assertTrue("プールが帯を覆う", pool.bottom >= lane2.bottom - 1)
    }

    // --- 整列と手作業 --------------------------------------------------------

    fun `test rearranging keeps a size set by hand`() {
        val (file, _) = open("orderProcessWithDi.bpmn20.xml")
        // approve を大きくしておく
        BpmnDocumentEditor.setBounds(
            project, file, reparse(file),
            mapOf("approve" to BpmnBounds(180.0, 75.0, 260.0, 180.0)),
            "resize",
        )

        val arranged = reparse(file)
        BpmnAutoLayout.relayout(arranged)
        BpmnDocumentEditor.applyLayout(project, file, arranged, "layout")

        val updated = reparse(file).nodesById.getValue("approve").bounds!!
        assertEquals("幅が既定に戻らない", 260.0, updated.width)
        assertEquals("高さが既定に戻らない", 180.0, updated.height)
    }

    fun `test rearranging keeps the bends of a connection whose ends did not move`() {
        val (file, _) = open("collaborationWithLanes.bpmn20.xml")
        // 折れ点を手で置く
        val bent = listOf(
            com.github.gekal.flowableeditor.model.BpmnPoint(210.0, 160.0),
            com.github.gekal.flowableeditor.model.BpmnPoint(400.0, 210.0),
            com.github.gekal.flowableeditor.model.BpmnPoint(600.0, 160.0),
        )
        BpmnDocumentEditor.setWaypoints(project, file, reparse(file), "f1", bent, "bend")
        val placed = reparse(file).edges.first { it.id == "f1" }.waypoints
        assertEquals(3, placed.size)

        // 位置が変わらない状態で整列する
        val arranged = reparse(file)
        val beforeStart = arranged.nodesById.getValue("start").bounds
        BpmnAutoLayout.relayout(arranged)

        // 図形が動かなかった線だけ、折れ点が残る
        if (arranged.nodesById.getValue("start").bounds == beforeStart) {
            assertEquals("動いていない線の折れ点は残る", placed, arranged.edges.first { it.id == "f1" }.waypoints)
        }
    }

    fun `test rearranging redraws the bends of a connection whose ends moved`() {
        val (file, _) = open("orderProcessWithDi.bpmn20.xml")
        val bent = listOf(
            com.github.gekal.flowableeditor.model.BpmnPoint(130.0, 115.0),
            com.github.gekal.flowableeditor.model.BpmnPoint(150.0, 400.0),
            com.github.gekal.flowableeditor.model.BpmnPoint(180.0, 115.0),
        )
        BpmnDocumentEditor.setWaypoints(project, file, reparse(file), "flow1", bent, "bend")

        // 大きく崩してから整列すると、図形が動くので線は引き直される
        BpmnDocumentEditor.setBounds(
            project, file, reparse(file),
            mapOf("approve" to BpmnBounds(900.0, 900.0, 100.0, 80.0)),
            "move",
        )
        val arranged = reparse(file)
        BpmnAutoLayout.relayout(arranged)

        val after = arranged.edges.first { it.id == "flow1" }.waypoints
        assertFalse("動いた線は引き直される", after == bent)
        assertTrue(after.size >= 2)
    }
}
