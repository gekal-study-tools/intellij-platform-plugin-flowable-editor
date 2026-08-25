package com.github.gekal.flowableeditor.editor

import com.github.gekal.flowableeditor.FlowableBundle
import com.github.gekal.flowableeditor.bpmn.FlowableIcons
import com.github.gekal.flowableeditor.edit.BpmnPaletteGroup
import com.github.gekal.flowableeditor.edit.BpmnPaletteItem
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.KeyStroke

/**
 * 図に置ける要素を並べた縦のパレット。
 *
 * ボタンを押すと道具が構えられ、次にキャンバスを押した位置へ置かれる。
 * Swing のドラッグ＆ドロップではなく「選んでから置く」形にしているのは、
 * 操作が途中で切れにくく、拡大縮小中でも扱いやすいため。
 *
 * 先頭に選択の道具を置き、Esc でも解除できるようにしてある。
 * 構えたまま抜け出せない状態を作らないための逃げ道。
 */
class BpmnPalette(private val onSelect: (BpmnPaletteItem?) -> Unit) : JPanel() {

    private val group = ButtonGroup()
    private val selectButton = createButton(
        icon = FlowableIcons.PaletteSelect,
        tooltip = FlowableBundle.message("palette.select"),
    ) { onSelect(null) }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(BpmnColors.GRID, 0, 0, 0, 1),
            JBUI.Borders.empty(4, 3),
        )
        background = BpmnColors.CANVAS

        selectButton.isSelected = true
        add(selectButton)

        var previous: BpmnPaletteGroup? = null
        for (item in BpmnPaletteItem.entries) {
            if (item.group != previous) {
                add(createSeparator(item.group))
                previous = item.group
            }
            add(
                createButton(
                    icon = FlowableIcons.forPaletteItem(item.iconName),
                    tooltip = item.label,
                ) { onSelect(item) },
            )
        }

        installEscape()
    }

    private fun createButton(icon: javax.swing.Icon, tooltip: String, onArm: () -> Unit): JToggleButton {
        val button = JToggleButton(icon).apply {
            toolTipText = tooltip
            alignmentX = Component.LEFT_ALIGNMENT
            isFocusable = false
            // 枠を消して、アイコンそのものが並んで見えるようにする
            isContentAreaFilled = false
            border = JBUI.Borders.empty(3)
            addActionListener { if (isSelected) onArm() }
        }
        button.maximumSize = Dimension(Int.MAX_VALUE, button.preferredSize.height)
        group.add(button)
        return button
    }

    /**
     * 組の区切り。
     *
     * パレットはアイコン 1 列ぶんの幅しかない。見出しを入れると切り詰められて
     * かえって読めないので、細い線だけを引き、組の名前は補助テキストで出す。
     */
    private fun createSeparator(paletteGroup: BpmnPaletteGroup): JComponent =
        JPanel().apply {
            toolTipText = paletteGroup.label
            alignmentX = Component.LEFT_ALIGNMENT
            background = BpmnColors.CANVAS
            border = JBUI.Borders.customLine(BpmnColors.GRID, 1, 0, 0, 0)
            val height = JBUI.scale(7)
            minimumSize = Dimension(0, height)
            preferredSize = Dimension(0, height)
            maximumSize = Dimension(Int.MAX_VALUE, height)
        }

    /** Esc で選択の道具に戻す。 */
    private fun installEscape() {
        val action = object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) = clearSelection()
        }
        getInputMap(WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "bpmn.palette.reset")
        actionMap.put("bpmn.palette.reset", action)
    }

    /** 構えている道具を外して選択に戻す。要素を置いた後にも呼ばれる。 */
    fun clearSelection() {
        selectButton.isSelected = true
        onSelect(null)
    }
}
