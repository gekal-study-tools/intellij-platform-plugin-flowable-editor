package com.github.gekal.flowableeditor.editor

import com.github.gekal.flowableeditor.FlowableBundle
import com.github.gekal.flowableeditor.bpmn.BpmnFiles
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * BPMN ファイルを「XML エディタ + 図プレビュー」の分割エディタで開く。
 */
class BpmnSplitEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean = BpmnFiles.isBpmnFile(file)

    /** 判定はファイル名と生のテキストだけで済むので read action は不要。 */
    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
        return BpmnTextEditorWithPreview(textEditor, BpmnPreviewFileEditor(project, file, textEditor))
    }

    override fun getEditorTypeId(): String = "flowable-bpmn-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

/**
 * XML エディタと図プレビューを左右に並べたエディタ。
 *
 * [bpmnPreview] を持たせているのは、プラットフォームの取得子に頼らず
 * 型の付いたままプレビュー側へ辿れるようにするため。
 */
class BpmnTextEditorWithPreview(
    editor: TextEditor,
    val bpmnPreview: BpmnPreviewFileEditor,
) : TextEditorWithPreview(
    editor,
    bpmnPreview,
    FlowableBundle.message("editor.name"),
    Layout.SHOW_EDITOR_AND_PREVIEW,
)
