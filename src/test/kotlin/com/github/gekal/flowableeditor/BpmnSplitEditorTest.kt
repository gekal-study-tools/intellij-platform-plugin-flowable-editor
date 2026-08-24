package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.editor.BpmnPreviewFileEditor
import com.github.gekal.flowableeditor.editor.BpmnSplitEditorProvider
import com.github.gekal.flowableeditor.editor.BpmnTextEditorWithPreview
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.util.Disposer
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
        myFixture.configureByFile(fileName)
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
}
