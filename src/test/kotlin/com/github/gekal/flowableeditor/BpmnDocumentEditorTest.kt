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
}
