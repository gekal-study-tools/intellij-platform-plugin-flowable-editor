package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.editor.BpmnPreviewFileEditor
import com.github.gekal.flowableeditor.editor.BpmnSplitEditorProvider
import com.github.gekal.flowableeditor.editor.BpmnTextEditorWithPreview
import com.github.gekal.flowableeditor.model.BpmnBounds
import com.github.gekal.flowableeditor.model.BpmnModelParser
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.util.Disposer
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * 分割エディタを実際に組み立てて動かす。
 *
 * 図の組み直しは「遅延 → PSI のコミット待ち → バックグラウンドでの read action」
 * と段を踏むため、単体の関数だけ見ていても壊れているのが分かりにくい。
 * ここで一度通しておく。
 */
class BpmnSplitEditorTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun openPreview(fileName: String): Pair<BpmnTextEditorWithPreview, BpmnPreviewFileEditor> {
        myFixture.configureFromTestData(fileName)
        val virtualFile = myFixture.file.virtualFile

        val provider = BpmnSplitEditorProvider()
        assertTrue("$fileName は BPMN として扱われる", provider.accept(project, virtualFile))

        val editor = provider.createEditor(project, virtualFile) as BpmnTextEditorWithPreview
        Disposer.register(testRootDisposable, editor)

        // TextEditorWithPreview は UI の組み立てを invokeLater に載せる。
        // 流しておかないと、テスト終了で破棄したあとに実行され、
        // 破棄済みエディタへの登録として後続のテストで落ちる。
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        return editor to editor.bpmnPreview
    }

    /**
     * 図が届くまで EDT のイベントを回す。
     * バックグラウンドの read action は finishOnUiThread で戻ってくる。
     */
    private fun waitForDiagram(preview: BpmnPreviewFileEditor) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            if (!preview.currentDiagram().isEmpty) return
            Thread.sleep(20)
        }
        fail("プレビューが図を組み立てませんでした")
    }

    fun `test refreshing the preview does not throw`() {
        // coalesceBy に共通すぎるキーを渡していると、ここで
        // IllegalArgumentException が同期的に飛ぶ
        val (_, preview) = openPreview("orderProcessWithDi.bpmn20.xml")
        preview.refreshNow()
    }

    fun `test the preview builds the diagram of the opened file`() {
        val (_, preview) = openPreview("orderProcessWithDi.bpmn20.xml")
        preview.refreshNow()
        waitForDiagram(preview)

        val diagram = preview.currentDiagram()
        assertTrue(diagram.hasDiagramInterchange)
        assertEquals(5, diagram.nodes.size)
        assertNotNull(diagram.nodesById["approve"])
    }

    fun `test the preview lays out a definition without diagram interchange`() {
        val (_, preview) = openPreview("subProcessWithoutDi.bpmn20.xml")
        preview.refreshNow()
        waitForDiagram(preview)

        val diagram = preview.currentDiagram()
        assertFalse(diagram.hasDiagramInterchange)
        assertTrue("自動レイアウトが全ノードに座標を与える", diagram.nodes.all { it.bounds != null })
    }

    fun `test the split editor replaces the plain text editor`() {
        val (editor, _) = openPreview("orderProcessWithDi.bpmn20.xml")

        assertEquals(FileEditorPolicy.HIDE_DEFAULT_EDITOR, BpmnSplitEditorProvider().policy)
        assertNotNull(editor.textEditor)
        assertSame(editor.bpmnPreview, editor.previewEditor)
    }

    fun `test other xml files keep their normal editor`() {
        val file = myFixture.configureByText("beans.xml", "<beans><bean id=\"a\"/></beans>")

        assertFalse(BpmnSplitEditorProvider().accept(project, file.virtualFile))
    }

    // --- 図からの編集 --------------------------------------------------------

    fun `test editing from the diagram writes back without a threading error`() {
        // 実際の経路は EDT から呼ばれる。PSI を引くだけでも読み取りアクセスが要るので、
        // ここを通しておかないと実行時にしか気付けない。
        val (_, preview) = openPreview("orderProcessWithDi.bpmn20.xml")
        preview.refreshNow()
        waitForDiagram(preview)

        preview.editListenerForTests().onBoundsChanged(
            "approve",
            BpmnBounds(400.0, 350.0, 100.0, 80.0),
            isResize = false,
        )
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val moved = BpmnModelParser.parse(myFixture.file as XmlFile).nodesById.getValue("approve").bounds!!
        assertEquals(400.0, moved.x)
        assertEquals(350.0, moved.y)
    }

    fun `test editing from the diagram also drags the connections`() {
        val (_, preview) = openPreview("orderProcessWithDi.bpmn20.xml")
        preview.refreshNow()
        waitForDiagram(preview)
        val before = BpmnModelParser.parse(myFixture.file as XmlFile).edges.first { it.id == "flow1" }.waypoints

        preview.editListenerForTests().onBoundsChanged(
            "approve",
            BpmnBounds(400.0, 350.0, 100.0, 80.0),
            isResize = false,
        )
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val after = BpmnModelParser.parse(myFixture.file as XmlFile).edges.first { it.id == "flow1" }.waypoints
        assertTrue("線が図形に付いてくる", after != before)
    }
}
