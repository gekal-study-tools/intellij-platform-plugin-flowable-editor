package com.github.gekal.flowableeditor.editor

import com.github.gekal.flowableeditor.FlowableBundle
import com.github.gekal.flowableeditor.bpmn.FlowableIcons
import com.github.gekal.flowableeditor.edit.BpmnPaletteItem
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JToggleButton

/**
 * 図に置ける要素を並べた縦のパレット。
 *
 * ボタンを押すと道具が構えられ、次にキャンバスを押した位置へ置かれる。
 * Swing のドラッグ＆ドロップではなく「選んでから置く」形にしているのは、
 * 操作が途中で切れにくく、拡大縮小中でも扱いやすいため。
 */
class BpmnPalette(private val onSelect: (BpmnPaletteItem?) -> Unit) : JPanel() {

    private val group = ButtonGroup()
    private val buttons = mutableListOf<JToggleButton>()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(4)
        background = BpmnColors.CANVAS

        add(
            JBLabel(FlowableBundle.message("palette.title")).apply {
                font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
                foreground = BpmnColors.MUTED_TEXT
                alignmentX = Component.LEFT_ALIGNMENT
                border = JBUI.Borders.emptyBottom(4)
            },
        )

        for (item in BpmnPaletteItem.entries) {
            val button = JToggleButton(FlowableIcons.forKind(item.kind)).apply {
                toolTipText = item.label
                alignmentX = Component.LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                isFocusable = false
                addActionListener { onSelect(if (isSelected) item else null) }
            }
            group.add(button)
            buttons += button
            add(button)
        }
    }

    /** 構えている道具を外す。要素を置いた後に呼ばれる。 */
    fun clearSelection() {
        group.clearSelection()
        buttons.forEach { it.isSelected = false }
    }
}
