package com.github.gekal.flowableeditor.editor

import com.github.gekal.flowableeditor.FlowableBundle
import com.github.gekal.flowableeditor.edit.BpmnDocumentEditor
import com.github.gekal.flowableeditor.edit.BpmnPaletteItem
import com.github.gekal.flowableeditor.model.BpmnAutoLayout
import com.github.gekal.flowableeditor.model.BpmnBounds
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnEdge
import com.github.gekal.flowableeditor.model.BpmnModelParser
import com.github.gekal.flowableeditor.model.BpmnNode
import com.github.gekal.flowableeditor.model.BpmnPoint
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.openapi.util.Computable
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
    private val palette = BpmnPalette { item ->
        canvas.armedPaletteItem = item
    }
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
        rootPanel.add(palette, BorderLayout.WEST)
        rootPanel.add(statusLabel, BorderLayout.SOUTH)

        // 書き込めるファイルのときだけ図から編集できるようにする
        canvas.editListener = EditHandler()

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

    /** 図からの編集を受ける口。実際の経路をテストから通すために公開している。 */
    @TestOnly
    internal fun editListenerForTests(): BpmnCanvasEditListener =
        requireNotNull(canvas.editListener) { "編集が有効になっていない" }

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

    // --- 図からの編集 --------------------------------------------------------

    /**
     * キャンバス上の操作を XML の書き換えに繋ぐ。
     *
     * 書き換えたあとは何もしない。ドキュメントの変更を受けて図が組み直されるので、
     * 画面の更新は既定の流れに任せる。
     */
    private inner class EditHandler : BpmnCanvasEditListener {

        override fun onBoundsChanged(elementId: String, bounds: BpmnBounds, isResize: Boolean) {
            edit { file, diagram ->
                BpmnDocumentEditor.setBounds(
                    project,
                    file,
                    diagram,
                    mapOf(elementId to bounds),
                    FlowableBundle.message(if (isResize) "edit.command.resize" else "edit.command.move"),
                )
            }
        }

        override fun onWaypointsChanged(edgeId: String, waypoints: List<BpmnPoint>) {
            edit { file, diagram ->
                BpmnDocumentEditor.setWaypoints(
                    project, file, diagram, edgeId, waypoints,
                    FlowableBundle.message("edit.command.bend"),
                )
            }
        }

        override fun onConnect(sourceId: String, targetId: String) {
            edit { file, diagram ->
                BpmnDocumentEditor.connect(
                    project, file, diagram, sourceId, targetId,
                    FlowableBundle.message("edit.command.connect"),
                )
            }
        }

        override fun onCreate(
            item: BpmnPaletteItem,
            bounds: BpmnBounds,
            containerId: String?,
            attachToId: String?,
        ) {
            palette.clearSelection()
            edit { file, diagram ->
                BpmnDocumentEditor.createElement(
                    project, file, diagram, item, bounds, containerId,
                    FlowableBundle.message("edit.command.create"),
                    attachToId = attachToId,
                )
            }
        }

        override fun onDelete(elementIds: List<String>) {
            edit { file, _ ->
                BpmnDocumentEditor.delete(
                    project, file, elementIds,
                    FlowableBundle.message("edit.command.delete"),
                )
            }
        }

        override fun onRename(elementId: String, name: String) {
            edit { file, _ ->
                BpmnDocumentEditor.setName(
                    project, file, elementId, name,
                    FlowableBundle.message("edit.command.rename"),
                )
            }
        }

        /**
         * 書き換えの共通部分。読み取り専用のファイルには手を出さない。
         *
         * ここは EDT (マウスを離した直後) から呼ばれる。PSI を引くだけでも
         * 読み取りアクセスが要るので read action で包む。
         */
        private fun edit(action: (XmlFile, BpmnDiagram) -> Unit) {
            if (project.isDisposed || !file.isValid || !file.isWritable) return

            val psiFile = ApplicationManager.getApplication().runReadAction(
                Computable { PsiManager.getInstance(project).findFile(file) as? XmlFile },
            ) ?: return

            action(psiFile, canvas.getDiagram())
            // 書き換えの結果をすぐ図に反映する (通常の遅延を待たない)
            updateDiagram()
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
        group.add(action("action.layout", AllIcons.Graph.Layout) { arrangeDiagram() })
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

    /**
     * 図を配置し直す。
     *
     * いまの座標を捨てて左から右へ並べ直し、線も引き直す。
     * 書き込めないファイルや、配置し直せない図では何もしない。
     */
    private fun arrangeDiagram() {
        if (!file.isWritable) return
        val current = canvas.getDiagram()
        if (current.isEmpty) {
            Messages.showInfoMessage(
                project,
                FlowableBundle.message("layout.unsupported.empty"),
                FlowableBundle.message("layout.unsupported.title"),
            )
            return
        }
        if (!BpmnAutoLayout.canRelayout(current)) {
            Messages.showInfoMessage(
                project,
                FlowableBundle.message("layout.unsupported.empty"),
                FlowableBundle.message("layout.unsupported.title"),
            )
            return
        }

        val psiFile = ApplicationManager.getApplication().runReadAction(
            Computable { PsiManager.getInstance(project).findFile(file) as? XmlFile },
        ) ?: return

        // 手を加える前の状態から組み直す。画面の図をそのまま並べ替えると、
        // ドラッグ中の下書きなど中途半端な状態を書き戻しかねない。
        val arranged = ApplicationManager.getApplication().runReadAction(
            Computable { BpmnModelParser.parse(psiFile) },
        )
        BpmnAutoLayout.relayout(arranged)

        BpmnDocumentEditor.applyLayout(
            project,
            psiFile,
            arranged,
            FlowableBundle.message("edit.command.layout"),
        )
        updateDiagram()
        // 並べ直すと図の大きさが変わるので、全体が見えるところへ戻す
        canvas.fitToWindow()
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
