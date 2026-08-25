package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.bpmn.FlowableIcons
import com.github.gekal.flowableeditor.edit.BpmnPaletteItem
import com.github.gekal.flowableeditor.editor.BpmnPalette
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

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
        // 名前を分けただけで同じ絵を指していては意味がない。実際に描いて見比べる。
        val rendered = BpmnPaletteItem.entries.associate { item ->
            item to render(FlowableIcons.forPaletteItem(item.iconName))
        }

        val duplicates = mutableListOf<String>()
        val entries = rendered.entries.toList()
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                if (entries[i].value.contentEquals(entries[j].value)) {
                    duplicates += "${entries[i].key.label} と ${entries[j].key.label}"
                }
            }
        }
        assertTrue("同じ見た目のアイコンがある: $duplicates", duplicates.isEmpty())

        // 何も描かれていない (真っ白な) アイコンが混ざっていないこと
        for ((item, pixels) in rendered) {
            assertTrue("${item.label} のアイコンが空", pixels.any { it != pixels[0] })
        }
    }

    /** アイコンを描いて画素の並びにする。 */
    private fun render(icon: javax.swing.Icon): IntArray {
        val image = BufferedImage(
            icon.iconWidth.coerceAtLeast(1),
            icon.iconHeight.coerceAtLeast(1),
            BufferedImage.TYPE_INT_ARGB,
        )
        val g = image.createGraphics()
        try {
            icon.paintIcon(null, g, 0, 0)
        } finally {
            g.dispose()
        }
        return IntArray(image.width * image.height) { index ->
            image.getRGB(index % image.width, index / image.width)
        }
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

    fun `test the palette renders its icons`() {
        val palette = BpmnPalette { }
        palette.size = palette.preferredSize
        // レイアウトしないと子が大きさ 0 のままで、枠しか描かれない
        palette.doLayout()
        palette.components.forEach { it.doLayout() }
        assertTrue("縦に並ぶ幅がある", palette.width in 1..80)

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

        // 右端の枠線を数えて通ってしまわないよう、内側だけを見る
        val background = image.getRGB(1, 1)
        var drawn = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width - 3) {
                if (image.getRGB(x, y) != background) drawn++
            }
        }
        assertTrue("アイコンが描かれている (見つかった画素: $drawn)", drawn > 400)
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
