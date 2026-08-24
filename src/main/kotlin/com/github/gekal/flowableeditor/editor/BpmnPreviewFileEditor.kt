package com.github.gekal.flowableeditor.editor

import com.github.gekal.flowableeditor.FlowableBundle
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnEdge
import com.github.gekal.flowableeditor.model.BpmnModelParser
import com.github.gekal.flowableeditor.model.BpmnNode
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.roundToInt

/**
 * 分割エディタの右側に出る図プレビュー。読み取り専用。
 *
 * ドキュメントが変わるたびに少し待ってから再パースし、
 * 図をクリックすると左のテキストエディタの該当箇所にキャレットを移す。
 * 逆にキャレットを動かすと対応する図形が選択される。
 */
class BpmnPreviewFileEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val textEditor: TextEditor,
) : UserDataHolderBase(), FileEditor {

    companion object {
        /** 打鍵のたびに再パースしないための待ち時間 (ms)。 */
        private const val REPARSE_DELAY = 250

        /** これを超える大きさのファイルはプレビューを諦める。 */
        private const val MAX_FILE_SIZE = 5L * 1024 * 1024

        private const val EXPORT_SCALE = 2.0

        /**
         * 再パースをまとめるためのキーの片割れ。
         * このプラグイン固有の物を混ぜて、他所の処理と衝突しないようにする。
         */
        private val COALESCE_KEY = Any()
    }

    private val canvas = BpmnDiagramCanvas()
    private val statusLabel = JBLabel().apply { border = JBUI.Borders.empty(2, 8) }
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val rootPanel = JPanel(BorderLayout())

    /** キャレット同期の再入防止フラグ。 */
    private var syncing = false
    private var firstUpdate = true

    init {
        // JBScrollPane 経由で置くこと。macOS のピンチ操作は JBViewport が仲介する。
        val scrollPane = JBScrollPane(canvas)
        scrollPane.border = JBUI.Borders.empty()
        // 図がビューポートより小さいときに縁が覗かないよう、背景を合わせておく
        scrollPane.viewport.background = BpmnColors.CANVAS
        scrollPane.background = BpmnColors.CANVAS

        rootPanel.add(createToolbar(), BorderLayout.NORTH)
        rootPanel.add(scrollPane, BorderLayout.CENTER)
        rootPanel.add(statusLabel, BorderLayout.SOUTH)

        canvas.onElementSelected = ::onDiagramSelection
        canvas.onZoomChanged = ::updateStatus

        FileDocumentManager.getInstance().getDocument(file)?.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) = scheduleUpdate()
            },
            this,
        )

        textEditor.editor.caretModel.addCaretListener(
            object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) = syncFromCaret()
            },
            this,
        )

        scheduleUpdate()
    }

    // --- モデル更新 ----------------------------------------------------------

    private fun scheduleUpdate() {
        if (alarm.isDisposed) return
        alarm.cancelAllRequests()
        alarm.addRequest({ updateDiagram() }, REPARSE_DELAY)
    }

    private fun updateDiagram() {
        if (project.isDisposed || !file.isValid) return
        if (file.length > MAX_FILE_SIZE) {
            statusLabel.text = FlowableBundle.message("preview.too.large")
            return
        }

        PsiDocumentManager.getInstance(project).performWhenAllCommitted {
            if (project.isDisposed || !file.isValid) return@performWhenAllCommitted

            // パースはバックグラウンドで行い、EDT は図の差し替えだけを担当する。
            // coalesceBy により、連続した編集では最後の 1 回だけが生き残る。
            // キーは 2 つ渡すこと。FileEditor や Project は「共通すぎるキー」として
            // 単独では拒否され、IllegalArgumentException になる。
            ReadAction.nonBlocking<BpmnDiagram> {
                val psiFile = PsiManager.getInstance(project).findFile(file) as? XmlFile
                    ?: return@nonBlocking BpmnDiagram.EMPTY
                BpmnModelParser.parse(psiFile)
            }
                .expireWith(this)
                .coalesceBy(COALESCE_KEY, this)
                .finishOnUiThread(ModalityState.defaultModalityState()) { diagram ->
                    canvas.setDiagram(diagram, fit = firstUpdate)
                    firstUpdate = false
                    updateStatus()
                }
                .submit(AppExecutorUtil.getAppExecutorService())
        }
    }

    /** 遅延を挟まずに図を組み直す。テストから同期的に走らせるために分けている。 */
    @TestOnly
    internal fun refreshNow() = updateDiagram()

    /** 現在プレビューが持っている図。 */
    @TestOnly
    internal fun currentDiagram(): BpmnDiagram = canvas.getDiagram()

    private fun updateStatus() {
        val diagram = canvas.getDiagram()
        val zoomPercent = (canvas.zoom * 100).roundToInt()
        statusLabel.text = if (diagram.isEmpty) {
            FlowableBundle.message("preview.status.empty")
        } else {
            val layout = if (diagram.hasDiagramInterchange) {
                FlowableBundle.message("preview.layout.di")
            } else {
                FlowableBundle.message("preview.layout.auto")
            }
            FlowableBundle.message(
                "preview.status",
                diagram.processNames.size,
                diagram.nodes.size,
                diagram.edges.size,
                layout,
                zoomPercent,
            )
        }
    }

    // --- 双方向のキャレット同期 ----------------------------------------------

    private fun onDiagramSelection(element: Any?) {
        if (syncing) return
        val offset = when (element) {
            is BpmnNode -> element.textOffset
            is BpmnEdge -> element.textOffset
            else -> return
        }
        val editor = textEditor.editor
        if (offset < 0 || offset > editor.document.textLength) return

        syncing = true
        try {
            editor.caretModel.moveToOffset(offset)
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        } finally {
            syncing = false
        }
    }

    private fun syncFromCaret() {
        if (syncing) return
        val element = canvas.getDiagram().elementAtOffset(textEditor.editor.caretModel.offset) ?: return
        if (element === canvas.selectedElement()) return
        syncing = true
        try {
            canvas.select(element, scroll = true)
        } finally {
            syncing = false
        }
    }

    // --- ツールバー ----------------------------------------------------------

    private fun createToolbar(): JComponent {
        val group = DefaultActionGroup(
            action("action.zoom.in", AllIcons.General.ZoomIn) { canvas.zoomIn() },
            action("action.zoom.out", AllIcons.General.ZoomOut) { canvas.zoomOut() },
            action("action.zoom.fit", AllIcons.General.FitContent) { canvas.fitToWindow() },
            action("action.zoom.actual", AllIcons.General.ActualZoom) { canvas.resetZoom() },
        )
        group.addSeparator()
        group.add(action("action.export.png", AllIcons.Actions.Download) { exportPng() })
        group.add(action("action.refresh", AllIcons.Actions.Refresh) { updateDiagram() })

        val toolbar = ActionManager.getInstance().createActionToolbar("FlowableBpmnPreview", group, true)
        toolbar.targetComponent = canvas
        return toolbar.component
    }

    private fun action(messageKey: String, icon: javax.swing.Icon, handler: () -> Unit) =
        object : DumbAwareAction(FlowableBundle.messagePointer(messageKey), icon) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun actionPerformed(e: AnActionEvent) = handler()
        }

    private fun exportPng() {
        val image = canvas.renderToImage(EXPORT_SCALE)
        if (image == null) {
            Messages.showInfoMessage(
                project,
                FlowableBundle.message("export.nothing"),
                FlowableBundle.message("export.title"),
            )
            return
        }

        val descriptor = FileSaverDescriptor(
            FlowableBundle.message("export.title"),
            FlowableBundle.message("export.description"),
            "png",
        )
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val target = dialog.save(null as VirtualFile?, file.nameWithoutExtension + ".png") ?: return

        try {
            ImageIO.write(image, "PNG", target.file)
        } catch (e: IOException) {
            Messages.showErrorDialog(
                project,
                FlowableBundle.message("export.failed", e.message ?: e.javaClass.simpleName),
                FlowableBundle.message("export.title"),
            )
        }
    }

    // --- FileEditor ----------------------------------------------------------

    override fun getComponent(): JComponent = rootPanel

    override fun getPreferredFocusedComponent(): JComponent = canvas

    override fun getName(): String = FlowableBundle.message("preview.name")

    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) = Unit

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun dispose() = Unit
}
