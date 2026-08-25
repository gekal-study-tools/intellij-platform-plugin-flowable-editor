package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.bpmn.FlowableIcons
import com.github.gekal.flowableeditor.edit.BpmnPaletteItem
import com.github.gekal.flowableeditor.editor.BpmnPalette
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JToggleButton

/**
 * パレットの見た目と、道具の構え方・外し方。
 *
 * 描いた結果は build/reports/bpmn-render/palette.png に残すので目視もできる。
 */
class BpmnPaletteTest : BasePlatformTestCase() {

    fun `test every palette item has its own icon`() {
        // 同じ絵を使い回していると、開始と終了、ユーザーとサービスが見分けられない
        val icons = BpmnPaletteItem.entries.map { FlowableIcons.forPaletteItem(it.iconName) }

        assertEquals(BpmnPaletteItem.entries.size, icons.size)
        assertEquals(
            "要素ごとに違うアイコンが割り当てられている",
            BpmnPaletteItem.entries.size,
            BpmnPaletteItem.entries.map { it.iconName }.toSet().size,
        )
        assertTrue("アイコンが読み込める", icons.all { it.iconWidth > 0 && it.iconHeight > 0 })
    }

    fun `test the icons actually look different from each other`() {
        // 名前を分けただけで同じ絵を指していては意味がない。
        // ラスタライズは実行環境によって効かないことがあるので、絵の元 (SVG) を突き合わせる。
        val drawings = BpmnPaletteItem.entries.associateWith { readIcon(it.iconName) }

        val duplicates = mutableListOf<String>()
        val entries = drawings.entries.toList()
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                if (entries[i].value == entries[j].value) {
                    duplicates += "${entries[i].key.label} と ${entries[j].key.label}"
                }
            }
        }
        assertTrue("同じ絵のアイコンがある: $duplicates", duplicates.isEmpty())
    }

    fun `test every icon actually draws something`() {
        for (item in BpmnPaletteItem.entries) {
            val drawing = readIcon(item.iconName)
            assertTrue(
                "${item.label} のアイコンに図形が無い",
                listOf("<circle", "<rect", "<path").any { drawing.contains(it) },
            )
        }
    }

    /** アイコンの元になっている SVG を読む。 */
    private fun readIcon(iconName: String): String {
        val path = "/icons/palette$iconName.svg"
        val stream = requireNotNull(javaClass.getResourceAsStream(path)) { "$path が見つからない" }
        return stream.bufferedReader().use { it.readText() }
    }

    fun `test items are grouped in bpmn order`() {
        // 同じ組が散らばっていると、区切りが意味をなさない
        val groups = BpmnPaletteItem.entries.map { it.group }
        val firstAppearance = groups.distinct()
        val regrouped = firstAppearance.flatMap { group -> groups.filter { it == group } }

        assertEquals("同じ組が続けて並んでいる", regrouped, groups)
    }

    fun `test arming and clearing a tool`() {
        val armed = mutableListOf<BpmnPaletteItem?>()
        val palette = BpmnPalette { armed += it }

        palette.clearSelection()

        assertEquals("解除すると道具が外れる", listOf(null), armed)
    }

    fun `test the palette lays its buttons out`() {
        // 最初にこれを書いたとき、レイアウトを走らせずに描いていたため子が
        // 大きさ 0 のままで、枠しか描かれていないのに検査が通ってしまった。
        val palette = BpmnPalette { }
        palette.size = palette.preferredSize
        palette.doLayout()

        val buttons = palette.components.filterIsInstance<JToggleButton>()
        assertEquals(
            "選択の道具 + 要素の数だけボタンがある",
            BpmnPaletteItem.entries.size + 1,
            buttons.size,
        )
        for (button in buttons) {
            assertTrue("ボタンに大きさがある: ${button.toolTipText}", button.width > 0 && button.height > 0)
        }
        assertTrue("縦に並ぶ幅がある", palette.width in 1..80)
    }

    fun `test the palette can be painted`() {
        // 描画そのもので落ちないこと。画素は実行環境で変わるので数えない。
        val palette = BpmnPalette { }
        palette.size = palette.preferredSize
        palette.doLayout()

        val image = BufferedImage(palette.width, palette.height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            palette.paint(g)
        } finally {
            g.dispose()
        }
        val output = File("build/reports/bpmn-render/palette.png")
        output.parentFile.mkdirs()
        ImageIO.write(image, "PNG", output)
    }

    // --- 構造ビューとの一貫性 ------------------------------------------------

    fun `test the structure view uses the same icon as the palette`() {
        // 木と図で同じ要素が違う絵に見えると、対応を取るのに手間がかかる
        for (item in BpmnPaletteItem.entries) {
            val fromPalette = FlowableIcons.forPaletteItem(item.iconName)
            val fromKind = FlowableIcons.forElement(
                item.kind,
                item.eventDefinition?.removeSuffix("EventDefinition"),
            )
            assertSame("${item.label} の絵が揃っていない", fromPalette, fromKind)
        }
    }

    fun `test kinds without a drawn icon fall back to their category`() {
        // 描き起こしていない種類でも、分類ごとの絵が返る
        val annotation = FlowableIcons.forElement(BpmnElementKind.TEXT_ANNOTATION)
        val manual = FlowableIcons.forElement(BpmnElementKind.MANUAL_TASK)

        assertNotNull(annotation)
        assertSame("分類の絵に落ちる", FlowableIcons.Task, manual)
    }
}
